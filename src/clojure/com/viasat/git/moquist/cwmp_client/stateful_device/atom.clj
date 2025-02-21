(ns com.viasat.git.moquist.cwmp-client.stateful-device.atom
  (:require [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [com.viasat.git.moquist.cwmp-client.anomalies :refer [anomaly?]]
            [com.viasat.git.moquist.cwmp-client.stateful-device :as stateful-device]
            [com.viasat.git.moquist.cwmp-client.stateful-device-set :as stateful-device-set]
            [com.viasat.git.moquist.cwmp-client.stateful-device.specs]
            [com.viasat.git.moquist.cwmp-client.util.merge-maps :refer [merge-maps]]
            [com.viasat.git.moquist.cwmp-client.util.tr069 :as tr069-util]
            [com.viasat.git.moquist.cwmp-client.validate-tr069-parameters :as validate-parameters]
            [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(s/def :com.viasat.git.moquist.cwmp-client.stateful-device.atom/swap-result any?)
(s/def :com.viasat.git.moquist.cwmp-client.stateful-device.atom/spvs-sources
  (s/map-of string? #{:cpe :acs}))
(s/def :com.viasat.git.moquist.cwmp-client.stateful-device.atom/supported-param-names
  (s/coll-of string?))
(s/def :com.viasat.git.moquist.cwmp-client.stateful-device.atom.object-instance/source
  #{:cpe :acs})
(s/def :com.viasat.git.moquist.cwmp-client.stateful-device.atom.object-instance/current-index-max
  integer?)
(s/def :com.viasat.git.moquist.cwmp-client.stateful-device.atom/object-instance
  (s/keys :req-un [:com.viasat.git.moquist.cwmp-client.stateful-device.atom.object-instance/source
                   :com.viasat.git.moquist.cwmp-client.stateful-device.atom.object-instance/current-index-max]))
(s/def :com.viasat.git.moquist.cwmp-client.stateful-device.atom/object-instances
  (s/map-of string? :com.viasat.git.moquist.cwmp-client.stateful-device.atom/object-instance))

(s/def :com.viasat.git.moquist.cwmp-client.stateful-device.atom/state
  (s/keys :req-un [:com.viasat.git.moquist.cwmp-client.stateful-device.atom/swap-result
                   :com.viasat.git.moquist.cwmp-client.stateful-device/spvs
                   :com.viasat.git.moquist.cwmp-client.stateful-device.atom/spvs-sources
                   :com.viasat.git.moquist.cwmp-client.stateful-device.atom/supported-param-names
                   :com.viasat.git.moquist.cwmp-client.stateful-device.atom/object-instances]))

(defn all->one [ks v]
  (->> ks
       (map #(vector % v))
       (into {})))

(defn ^:private -set-parameter-values [state origin kvs]
  (let [validation-result (validate-parameters/validate-parameters kvs)]
    (cond
      (not (s/valid? :com.viasat.git.moquist.cwmp-client.stateful-device.atom/state state))
      (assoc state
             :swap-result
             (merge {:cognitect.anomalies/category :cognitect.anomalies/incorrect}
                    (s/explain-data :com.viasat.git.moquist.cwmp-client.stateful-device.atom/state state)))

      (not (s/valid? :com.viasat.git.moquist.cwmp-client.stateful-device/spvs kvs))
      (assoc state
             :swap-result
             (merge {:cognitect.anomalies/category :cognitect.anomalies/incorrect}
                    (s/explain-data :com.viasat.git.moquist.cwmp-client.stateful-device/spvs kvs)))

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

(defrecord StatefulDeviceAtom [DeviceId acs-url state cwmp-client-fn]
  component/Lifecycle
  (start [component]
    (log/trace "Starting StatefulDeviceAtom" DeviceId)
    (if-not cwmp-client-fn
      (do
        (log/trace (format "StatefulDeviceAtom found no cwmp-client-fn, returning inert stateful-device"))
        component)
      (let [stopper (atom false)]
        (log/trace (format "StatefulDeviceAtom found cwmp-client-fn, starting worker thread"))
        (.start (Thread. #(while (not @stopper)
                            (cwmp-client-fn component))))
        (assoc component :cwmp-client-stopper stopper))))
  (stop [component]
    (log/trace "Stopping StatefulDeviceAtom" DeviceId)
    (if-not cwmp-client-fn
      component
      (do
        (reset! (:cwmp-client-stopper component) true)
        (dissoc component :cwmp-client-stopper))))

  stateful-device/StatefulDevice
  (-get-device-id [_] DeviceId)
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
    (:processor-state @state)))

(def ParameterList-base
  {:Device.DeviceInfo.Manufacturer "cwmp-test-manufacturer"
   ;; second hex char E marks this as "locally administered", i.e., never used on a real device.
   :Device.DeviceInfo.SerialNumber "FEFEFE000000"
   :Device.DeviceInfo.HardwareVersion "cwmp-test-hardware-version"
   :Device.DeviceInfo.SoftwareVersion "software_version"
   :Device.DeviceInfo.ProductClass "product-class"
   :Device.DeviceInfo.ManufacturerOUI "FEFEFE"
   ;; 3.6.2 Object Instance Wildcards Requirements
   :ManagementServer.InstanceWildcardsSupported true})

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
   :processor-state nil})

(defn map->string-keys
  [kvs]
  (->> kvs
       (map (fn [[k v]] [(name k) v]))
       (into {})))

(defn stateful-device-atom [mac-address acs-url cpe-parameter-values & [{:keys [cwmp-client-fn supported-param-names]}]]
  (let [{:keys [DeviceId ParameterList]} (device-cpe-spvs-init mac-address)
        cpe-parameter-values (map->string-keys (merge ParameterList cpe-parameter-values))
        stateful-device (StatefulDeviceAtom. DeviceId
                                             acs-url
                                             (atom (initial-device-state supported-param-names))
                                             cwmp-client-fn)
        spv-result (stateful-device/cpe-set-parameter-values! stateful-device cpe-parameter-values)]
    (if (anomaly? spv-result)
      spv-result
      stateful-device)))

(comment
  (stateful-device/get-parameter-values (stateful-device-atom "FEFEFE123456" "fake-acs-url" {}) [])
  (def mef (stateful-device-atom "FEFEFE123456" {} {:supported-param-names #{"ab" "cd"}})))

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
    (let [{:keys [instance-count mac-addr-oui acs-url cwmp-client-fn]} config
          devices (reduce (fn [r i]
                            (let [mac-addr (get-next-macr-addr mac-addr-oui i)]
                              (assoc r mac-addr (component/start
                                                 (stateful-device-atom mac-addr
                                                                       acs-url
                                                                       {}
                                                                       {:cwmp-client-fn cwmp-client-fn})))))
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
