(ns viasat.cwmp-cpe.stateful-device.atom
  (:require [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [viasat.cwmp-cpe.anomalies :refer [anomaly?]]
            [viasat.cwmp-cpe.stateful-device :as stateful-device]
            [viasat.cwmp-cpe.stateful-device-set :as stateful-device-set]
            [viasat.cwmp-cpe.stateful-device.specs]
            [viasat.cwmp-cpe.util.merge-maps :refer [merge-maps]]
            [viasat.cwmp-cpe.util.tr069 :as tr069-util]
            [viasat.cwmp-cpe.validate-tr069-parameters :as validate-parameters]))

(set! *warn-on-reflection* true)

(s/def :viasat.cwmp-cpe.stateful-device.atom/swap-result any?)
(s/def :viasat.cwmp-cpe.stateful-device.atom/spvs-sources
  (s/map-of string? #{:cpe :acs}))
(s/def :viasat.cwmp-cpe.stateful-device.atom/supported-param-names
  (s/coll-of string?))
(s/def :viasat.cwmp-cpe.stateful-device.atom.object-instance/source
  #{:cpe :acs})
(s/def :viasat.cwmp-cpe.stateful-device.atom.object-instance/current-index-max
  integer?)
(s/def :viasat.cwmp-cpe.stateful-device.atom/object-instance
  (s/keys :req-un [:viasat.cwmp-cpe.stateful-device.atom.object-instance/source
                   :viasat.cwmp-cpe.stateful-device.atom.object-instance/current-index-max]))
(s/def :viasat.cwmp-cpe.stateful-device.atom/object-instances
  (s/map-of string? :viasat.cwmp-cpe.stateful-device.atom/object-instance))

(s/def :viasat.cwmp-cpe.stateful-device.atom/state
  (s/keys :req-un [:viasat.cwmp-cpe.stateful-device.atom/swap-result
                   :viasat.cwmp-cpe.stateful-device/spvs
                   :viasat.cwmp-cpe.stateful-device.atom/spvs-sources
                   :viasat.cwmp-cpe.stateful-device.atom/supported-param-names
                   :viasat.cwmp-cpe.stateful-device.atom/object-instances]))

(defn all->one [ks v]
  (->> ks
       (map #(vector % v))
       (into {})))

(defn ^:private -set-parameter-values [state origin kvs]
  (let [validation-result (validate-parameters/validate-parameters kvs)]
    (cond
      (not (s/valid? :viasat.cwmp-cpe.stateful-device.atom/state state))
      (assoc state
             :swap-result
             (merge {:cognitect.anomalies/category :cognitect.anomalies/incorrect}
                    (s/explain-data :viasat.cwmp-cpe.stateful-device.atom/state state)))

      (not (s/valid? :viasat.cwmp-cpe.stateful-device/spvs kvs))
      (assoc state
             :swap-result
             (merge {:cognitect.anomalies/category :cognitect.anomalies/incorrect}
                    (s/explain-data :viasat.cwmp-cpe.stateful-device/spvs kvs)))

      (anomaly? validation-result)
      (assoc state :swap-result validation-result)

      :else (-> state
                (update :spvs merge kvs)
                (update :spvs-sources merge (all->one (keys kvs) origin))
                (assoc :swap-result nil)))))

(defn set-parameter-values! [state origin kvs]
  (:swap-result (swap! state -set-parameter-values origin kvs)))

(defn get-parameter-values [kvs wildcard-paths]
  (let [available-parameter-names (keys kvs)
        matched-parameter-names (tr069-util/tr069-wildcard-path-match wildcard-paths available-parameter-names)]
    (select-keys kvs matched-parameter-names)))

(defn get-parameter-names [wildcard-path kvs supported-param-names]
  (let [available-parameter-names (into supported-param-names (keys kvs))
        wildcard-paths (if (seq wildcard-path)
                         [wildcard-path]
                         wildcard-path)]
    (tr069-util/tr069-wildcard-path-match wildcard-paths available-parameter-names)))

(defn add-object!
  [state object-name parameter-key]
  (let [object-source :acs] ; maybe this becomes a param, if we ever support adding objects from the cpe
    (:swap-result
     (swap! state
            (fn [{:as state :keys [object-instances supported-param-names]}]
              (let [param-key-updates (when parameter-key
                                        (-> state
                                            (select-keys [:spvs :spvs-sources])
                                            (assoc-in [:spvs "Device.ManagementServer.ParameterKey"] parameter-key)
                                            (assoc-in [:spvs-sources "Device.ManagementServer.ParameterKey"] object-source)))
                    supported-param-names (conj supported-param-names object-name)
                    current-index-max (get-in object-instances [object-name :current-index-max])
                    current-index-max (if (integer? current-index-max)
                                        (inc current-index-max)
                                         ;; init to 0
                                        0)
                    object-instances (assoc object-instances
                                            object-name {:source object-source
                                                         :current-index-max current-index-max})]
                (cond-> (assoc state
                               :supported-param-names supported-param-names
                               :object-instances object-instances
                                ;; Table 30 -- AddObjectResponse arguments
                               :swap-result {:instance-number current-index-max})
                  param-key-updates (merge-maps param-key-updates))))))))

(defn username->password
  "In order to authenticate Connection Requests, the :cnr-server must be able to look up the password from the given username. If the username is wrong for the given device, that's also an error and there's no reason to return the password in that case."
  [stateful-device username]
  (let [spvs (-> stateful-device :state deref :spvs)
        up (get-parameter-values spvs ["Device.ManagementServer.ConnectionRequestUsername"
                                       "Device.ManagementServer.ConnectionRequestPassword"])
        my-username (get up "Device.ManagementServer.ConnectionRequestUsername")
        my-password (get up "Device.ManagementServer.ConnectionRequestPassword")]
    (if (= username my-username)
      my-password
      {:cognitect.anomalies/category :cognitect.anomalies/incorrect
       :message (format "Incorrect username for device %s"
                        (stateful-device/get-device-id stateful-device))})))

(defn notify-cnr! [{:keys [state] :as _stateful-device}]
  (swap! state assoc :cnr-at-millis (System/currentTimeMillis)))

(def ^:dynamic *cnr-delay-millis*
  "3.2.2.2 HTTP Specific Connection Request Requirements
  The CPE’s response to a successfully authenticated Connection Request MUST use
  either a “200 (OK)” or a “204 (No Content)” HTTP status code. The CPE MUST
  send this response immediately upon successful authentication, prior to it initiating
  the resulting Session.

  *cnr-delay-millis* is a hackish way of allowing the cnr-server to send the HTTP response prior to the CNR inform being sent from the CPE."
  5000)
(defn cnr-now? [{:keys [state] :as _stateful-device}]
  (let [cnr-at-millis (:cnr-at-millis @state)]
    (when (integer? cnr-at-millis)
      (Thread/sleep (max 0
                         (- (System/currentTimeMillis)
                            *cnr-delay-millis*
                            cnr-at-millis)))
      true)))

(defn cnr-reset! [{:keys [state] :as _stateful-device}]
  (swap! state assoc :cnr-at-millis nil))

(defrecord StatefulDeviceAtom [acs-url state config]
  component/Lifecycle
  (start [component]
    (let [{:keys [DeviceId cwmp-cpe-fn cnr-host cnr-port]} config
          connection-request-url {"Device.ManagementServer.ConnectionRequestURL"
                                  ;; TODO: move /cpes/ into configs to avoid hard-coded dependency with ring-handler
                                  ;; The Connection Request MUST make use of HTTP, not HTTPS. The associated URL MUST be an HTTP URL.
                                  ;; 3.2.2.2 HTTP Specific Connection Request Requirements
                                  (format "http://%s:%s/cpes/%s"
                                          cnr-host
                                          cnr-port
                                          (:SerialNumber DeviceId))}]
      (log/trace "Starting StatefulDeviceAtom" DeviceId)
      (stateful-device/cpe-set-parameter-values! component connection-request-url)
      (if-not cwmp-cpe-fn
        (do
          (log/trace (format "StatefulDeviceAtom found no cwmp-cpe-fn, returning inert stateful-device"))
          component)
        (let [stopper (atom false)]
          (log/trace (format "StatefulDeviceAtom found cwmp-cpe-fn, starting worker thread"))
          (.start (Thread. #(while (not @stopper)
                              (cwmp-cpe-fn component))))
          (assoc component :cwmp-cpe-stopper stopper)))))
  (stop [component]
    (let [{:keys [DeviceId cwmp-cpe-fn]} config]
      (log/trace "Stopping StatefulDeviceAtom" DeviceId)
      (if-not cwmp-cpe-fn
        component
        (do
          (reset! (:cwmp-cpe-stopper component) true)
          (dissoc component :cwmp-cpe-stopper)))))

  stateful-device/StatefulDevice
  (-get-device-id [_]
    (get config :DeviceId))
  (-cpe-set-parameter-values! [_ kvs]
    (set-parameter-values! state :cpe kvs))
  (-acs-set-parameter-values! [_ kvs]
    (set-parameter-values! state :acs kvs))
  (-get-parameter-values [_ names]
    ;; XXX handle anomalies
    (get-parameter-values (:spvs @state) names))
  (-get-parameter-names [_ param-path]
    ;; TODO: NextLevel suppport
    (get-parameter-names param-path
                         (:spvs @state)
                         (:supported-param-names @state)))
  (-get-parameter-values-sources [_ names]
    ;; XXX handle anomalies
    (get-parameter-values (:spvs-sources @state) names))
  (-acs-add-object! [_ object-name parameter-key]
    (add-object! state object-name parameter-key))
  (-update-processor-state! [_ f]
    (swap! state update :processor-state f))
  (-get-processor-state [_]
    (:processor-state @state))
  (-username->password [this username]
    (username->password this username))
  (-notify-cnr! [this]
    (notify-cnr! this))
  (-cnr-now? [this]
    (cnr-now? this))
  (-cnr-reset! [this]
    (cnr-reset! this))
  (-dump [_] @state))

(def ParameterList-base
  {:Device.DeviceInfo.Manufacturer "cwmp-test-manufacturer"
   ;; second hex char E marks this as "locally administered", i.e., never used on a real device.
   :Device.DeviceInfo.SerialNumber "FEFEFE000000"
   :Device.DeviceInfo.HardwareVersion "cwmp-test-hardware-version"
   :Device.DeviceInfo.SoftwareVersion "software_version"
   :Device.DeviceInfo.ProductClass "product-class"
   :Device.DeviceInfo.ManufacturerOUI "FEFEFE"
   ;; 3.6.2 Object Instance Wildcards Requirements
   :Device.ManagementServer.InstanceWildcardsSupported true})

(def DeviceIdStruct-base
  "DeviceId as defined in the TR-069 standard:
  \"A structure that uniquely identifies the CPE.\"
  See A.3.3.1 and Table 39 in TR-069_Amendment-6.pdf"
  {:Manufacturer "manufacturer"
   :ProductClass "product-class"
   :SerialNumber "serial-number"
   :OUI "FEFEFE"})

(defn device-cpe-spvs-init [mac-address]
  (let [oui (subs mac-address 0 6)]
    (merge-maps
     {:ParameterList ParameterList-base
      :DeviceId DeviceIdStruct-base}
     {:ParameterList {:Device.DeviceInfo.SerialNumber mac-address
                      :Device.DeviceInfo.ManufacturerOUI oui}
      :DeviceId {:SerialNumber mac-address
                 :OUI oui}})))

(defn initial-device-state [supported-param-names]
  {:swap-result nil
   :spvs {}
   :spvs-sources {}
   :supported-param-names (or supported-param-names #{})
   :object-instances {}
   :cnr-at-millis nil
   :processor-state nil})

(defn map->string-keys
  [kvs]
  (->> kvs
       (map (fn [[k v]] [(name k) v]))
       (into {})))

(defn stateful-device-atom [mac-address cpe-parameter-values
                            & [{:keys [acs-url cnr-host cnr-port cwmp-cpe-fn supported-param-names]}]]
  (let [{:keys [DeviceId ParameterList]} (device-cpe-spvs-init mac-address)
        cpe-parameter-values (merge
                              (map->string-keys ParameterList)
                              (map->string-keys cpe-parameter-values))
        stateful-device (StatefulDeviceAtom. acs-url
                                             (atom (initial-device-state supported-param-names))
                                             {:cwmp-cpe-fn cwmp-cpe-fn
                                              :DeviceId DeviceId
                                              :cnr-host cnr-host
                                              :cnr-port cnr-port})
        spv-result (stateful-device/cpe-set-parameter-values! stateful-device cpe-parameter-values)]
    (if (anomaly? spv-result)
      spv-result
      stateful-device)))

(comment
  (stateful-device/get-parameter-values (stateful-device-atom "FEFEFE123456" {:acs-url "fake-acs-url"}) [])
  (def mef (stateful-device-atom "FEFEFE123456" {:acs-url "hi" :supported-param-names #{"ab" "cd"}})))

(defn get-next-macr-addr [prefix counter]
  (let [basehex (-> counter
                    (Integer/toHexString)
                    (.toUpperCase))
        lacking-chars (- 12 (+ (count prefix) (count basehex)))]
    (str prefix (apply str (repeat lacking-chars "0")) basehex)))

(comment
  (get-next-macr-addr "FEFEFE000000" 1))

(defrecord StatefulDeviceAtomSet [config devices]
  component/Lifecycle
  (start [component]
    (let [{:keys [instance-count mac-addr-oui acs-url cwmp-cpe-fn cnr-host cnr-port cpe-parameter-values]} config
          devices (reduce (fn [r i]
                            (let [mac-addr (get-next-macr-addr mac-addr-oui i)]
                              (assoc r mac-addr (component/start
                                                 (stateful-device-atom mac-addr
                                                                       cpe-parameter-values
                                                                       {:acs-url acs-url
                                                                        :cwmp-cpe-fn cwmp-cpe-fn
                                                                        :cnr-host cnr-host
                                                                        :cnr-port cnr-port})))))
                          {}
                          (range instance-count))]
      (assoc component :devices (atom devices))))
  (stop [component]
    (doseq [[_mac-addr device] @devices]
      (component/stop device))
    (assoc component :devices nil))

  stateful-device-set/StatefulDeviceSet
  (-get-device [_ device-id]
    (get @devices device-id))
  (-list-devices [_]
    (keys @devices)))

(defn stateful-device-atom-set [config]
  (map->StatefulDeviceAtomSet {:config config}))
