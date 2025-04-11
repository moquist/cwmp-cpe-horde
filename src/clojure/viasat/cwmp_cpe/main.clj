(ns viasat.cwmp-cpe.main
  (:require
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]
   [timbre-json-appender.core :as tja]
   [viasat.cwmp-cpe.basic-cpe :as basic-cpe]
   [viasat.cwmp-cpe.stateful-device :as stateful-device]
   [viasat.cwmp-cpe.system :as system]
   [viasat.cwmp-cpe.tr069-type-transforms :as tr069-type-transforms]))

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

(defn configure-tr069!
  ;; XXX Does this really belong here? Where would be better?
  "Provide for custom extension of the TR181 data model with xsd type specs."
  [{:keys [additional-tr181-name->xsd-type]}]
  (swap! tr069-type-transforms/xsi-types merge additional-tr181-name->xsd-type))

(defn system-go [config]
  (try
    (configure-logging! (:logging config))
    (configure-tr069! (:tr069 config))
    (log/debug {:active-config config})
    (-> (system/system-map config)
        (system/start-system))
    (catch Throwable t
      (log/fatal "Caught error starting system, throwing exception without components")
      (throw (component/ex-without-components t)))))

(defn -main [& _]
  (try
    (system-go (assoc-in (fetch-system-config)
                         ;; XXX This is mildly gross, and non-extensible. Consider better options.
                         [:stateful-device-set :cwmp-cpe-fn] basic-cpe/cwmp-cpe-fn))
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
               [:stateful-device-set :cwmp-cpe-fn] basic-cpe/cwmp-cpe-fn))
    :done)

  (system/stop-system)

  (def sd (get (deref (:devices (:stateful-device-set @system/system)))
               "FEFEFE000004"))
  (stateful-device/get-processor-state sd)
  (stateful-device/cnr-now? sd)
  (-> sd :state deref :cnr-at-millis)

  #_(clojure.pprint/pprint sd)
  #_(clojure.pprint/pprint (keys  (stateful-device/get-parameter-values sd [])))

  (format "%s:%s"
          (second (first (stateful-device/get-parameter-values sd ["Device.ManagementServer.ConnectionRequestUsername"])))
          (second (first (stateful-device/get-parameter-values sd ["Device.ManagementServer.ConnectionRequestPassword"]))))
  ;; curl -i --digest -v -u "TAmDWH15sFSH:ZvvH7xjfmLrf" localhost:9000/cpes/FEFEFE000004

  (change-param-values! sd {"Device.DeviceInfo.SoftwareVersion" "monkey_1.2.7"}))
