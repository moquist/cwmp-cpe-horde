(ns com.viasat.git.moquist.cwmp-cpe.system
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [com.stuartsierra.component :as component]
   [com.viasat.git.moquist.cwmp-cpe.http.cnr-server :as cnr-server]
   [com.viasat.git.moquist.cwmp-cpe.stateful-device.atom :as stateful-device-atom]
   [com.viasat.git.moquist.cwmp-cpe.stateful-device.fod :as stateful-device-fod]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.cnr-server/host string?)
(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.cnr-server/port
  (s/and integer?
         #(< 0 % 65536)))
(s/def :com.viasat.git.moquist.cwmp-cpe.system.config/cnr-server
  (s/keys :req-un [:com.viasat.git.moquist.cwmp-cpe.system.config.cnr-server/host
                   :com.viasat.git.moquist.cwmp-cpe.system.config.cnr-server/port]))

(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/backend keyword?)
(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/instance-count pos-int?)
(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/mac-addr-oui
  (s/and string? #(re-matches #"[0-9A-F]{6}" %)))
(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/acs-url string?)
(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/cpe-parameter-values
  (s/map-of string? any?))

(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.tr069/additional-tr181-name->xsd-type
  (s/map-of string? string?))

(s/def :com.viasat.git.moquist.cwmp-cpe.system.config/tr069
  (s/keys :opt-un [:com.viasat.git.moquist.cwmp-cpe.system.config.tr069/additional-tr181-name->xsd-type]))

(s/def :com.viasat.git.moquist.cwmp-cpe.system.config/stateful-device-set
  (s/keys :req-un [:com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/backend
                   :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/instance-count
                   :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/mac-addr-oui
                   :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/acs-url
                   :com.viasat.git.moquist.cwmp-cpe.system.config.stateful-device-set/cpe-parameter-values]))

(s/def :com.viasat.git.moquist.cwmp-cpe.system.config.logging/level
  ;; specific to https://github.com/taoensso/timbre/blob/master/wiki/1-Getting-started.md#minimum--level
  #{:trace :debug :info :warn :error :fatal :report})
(s/def :com.viasat.git.moquist.cwmp-cpe.system.config/logging
  (s/keys :req-un [:com.viasat.git.moquist.cwmp-cpe.system.config.logging/level]))

(s/def :com.viasat.git.moquist.cwmp-cpe.system.config/http-api
  (s/keys :req-un [:com.viasat.git.moquist.cwmp-cpe.system.config.http-api/port]))

(s/def :com.viasat.git.moquist.cwmp-cpe.system/config
  (s/keys :req-un [:com.viasat.git.moquist.cwmp-cpe.system.config/logging
                   :com.viasat.git.moquist.cwmp-cpe.system.config/cnr-server
                   :com.viasat.git.moquist.cwmp-cpe.system.config/stateful-device-set]))

(defn load-config
  [config-file-path]
  (when-not (.exists (io/file config-file-path))
    (throw (Exception. (format "Unable to read config file at path: %s" config-file-path))))

  (let [config (edn/read-string (slurp config-file-path))]
    (when-not (s/valid? :com.viasat.git.moquist.cwmp-cpe.system/config config)
      (throw (ex-info (format "config file at %s is not valid" config-file-path)
                      (s/explain-data :com.viasat.git.moquist.cwmp-cpe.system/config config))))
    config))

(defmulti with-component
  "For conditionally adding configuration-dependent components. Should
  return a possibly-updated system map.

  Should _not_ inspect system map or rummage around in the
  configration for other components.

  Likewise, there should be no further processing of the component
  config other than fetching the appropriate value from the system
  config.

  Note that `with-component` implementations include component-using
  associations. This does make it less clear when looking at the
  system-map construction which components depend on which
  others. However, these component dependencies may vary with the
  system configuration, so they belong in these with-component
  construtors. Not ideal, but given the trade-offs, keeping these
  dependency definitions here is what we're doing.
  "
  (fn [_system-map _config component-key] component-key))

(defmethod with-component :default
  [_system-map _config component-key]
  (throw (ex-info "Cannot construct system-map with unknown component key"
                  {:component-key component-key})))

(defmethod with-component :cnr-server
  [system-map config component-key]
  (let [component-config (component-key config)
        component (component/using (cnr-server/http-api component-config)
                                   [:ring-handler])]
    (assoc system-map component-key component)))

(defmethod with-component :ring-handler
  [system-map config component-key]
  (let [component-config (component-key config)
        component (component/using (cnr-server/ring-handler component-config)
                                   [:stateful-device-set])]
    (assoc system-map component-key component)))

(defmethod with-component :stateful-device-set
  [system-map config component-key]
  (let [;; TODO: SMELL. How should this be done better?
        ;; The stateful-device set needs to know the CNR host:port.
        {cnr-host :host cnr-port :port} (:cnr-server config)
        component-config (assoc (component-key config)
                                :cnr-host cnr-host
                                :cnr-port cnr-port)
        backend (:backend component-config)
        component (condp = backend
                    :in-memory
                    (stateful-device-atom/stateful-device-atom-set component-config)

                    :files-on-disk
                    (stateful-device-fod/stateful-device-fod-set component-config)

                    (let [message (format "Unexpected stateful-device-set backend: %s" backend)]
                      (throw (ex-info message
                                      {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                                       :message message}))))]
    (assoc system-map component-key component)))

(defn build-system-map [config]
  (-> {}
      (with-component config :stateful-device-set)
      (with-component config :cnr-server)
      (with-component config :ring-handler)))

(defn system-map [config]
  ;; TODO: maybe abstract system-map config here
  (build-system-map config))

(defonce system
  (atom nil))

(defn start-system
  "Start the system and store it in the system atom."
  [sys-map]
  (if @system
    (log/info "System is started")
    (reset! system (component/start (apply component/system-map (mapcat identity sys-map))))))

(defn stop-system
  "Stop the system and clear the system atom."
  []
  (if-let [sys @system]
    (try
      (log/info "Stopping")
      (component/stop sys)
      (reset! system nil)
      (catch Throwable ex
        (log/fatal "Shutdown failed" {:throwable ex}))
      (finally
        (log/info "Stopped")))
    (log/info "Shutdown requested, but system is not initialized.")))
