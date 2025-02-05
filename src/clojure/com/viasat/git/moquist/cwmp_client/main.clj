(ns com.viasat.git.moquist.cwmp-client.main
  (:require
   [com.stuartsierra.component :as component]
   [com.viasat.git.moquist.cwmp-client.basic-client :as basic-client]
   [com.viasat.git.moquist.cwmp-client.stateful-device :as stateful-device]
   [com.viasat.git.moquist.cwmp-client.system :as system]
   [taoensso.timbre :as log]
   [timbre-json-appender.core :as tja]))

(set! *warn-on-reflection* true)

(defn fetch-system-config []
  (if-let [config-file-path (System/getenv "CONFIG_FILE_PATH")]
    (do
      (log/trace (format "Loading config file from CONFIG_FILE_PATH: %s" config-file-path))
      (system/load-config config-file-path))
    (throw (Exception. "CONFIG_FILE_PATH env var is unset"))))

(defn configure-logging!
  [{:keys [format level]}]
  (when (= format :json)
    ;; JSON logs are great for prod & automation.
    ;; Non-JSON logs are almost always better for humans.
    (tja/install))
  (log/set-min-level! level))

(defn system-go [config]
  (try
    (configure-logging! (:logging config))
    (log/debug {:active-config config})
    (-> (system/system-map config)
        (system/start-system))
    (catch Throwable t
      (log/fatal "Caught error starting system, throwing exception without components")
      (throw (component/ex-without-components t)))))

(defn -main [& _]
  (try
    (system-go (assoc-in (fetch-system-config)
                         [:stateful-device-set :cwmp-client-fn] basic-client/cwmp-client-fn))
    (catch Throwable t
      (log/fatal "Caught error starting system, throwing exception without components")
      (throw (component/ex-without-components t)))))

(defn change-param-values! [stateful-device parameter-values]
  (stateful-device/cpe-set-parameter-values! stateful-device parameter-values)
  (stateful-device/update-processor-state!
   stateful-device
   (fn [{:as processor-state :keys [value-change-parameter-names]}]
     (assoc processor-state
            :value-change-parameter-names
            (into value-change-parameter-names (keys parameter-values))))))

(comment
  (do
    (log/set-min-level! :trace)
    (system-go
     (assoc-in (system/load-config "config-preprod.edn")
               [:stateful-device-set :cwmp-client-fn] basic-client/cwmp-client-fn))
    :done)

  (system/stop-system)

  (def sd (get (deref (:devices (:stateful-device-set @system/system)))
               "FEFEFE000004"))
  (stateful-device/get-processor-state sd)
  (change-param-values! sd {"Device.DeviceInfo.SoftwareVersion" "monkey_1.2.7"}))
