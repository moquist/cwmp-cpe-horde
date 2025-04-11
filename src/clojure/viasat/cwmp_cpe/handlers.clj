(ns viasat.cwmp-cpe.handlers
  (:require [taoensso.timbre :as log]
            [viasat.cwmp-cpe.anomalies :refer [anomaly?]]
            [viasat.cwmp-cpe.messages :as messages]
            [viasat.cwmp-cpe.stateful-device :as stateful-device]
            [viasat.cwmp-cpe.tr069-type-transforms :as tr069-type-transforms]
            [viasat.cwmp-cpe.util.tr069.summarize :as summarize-util]
            [viasat.cwmp-cpe.util.value-finders :as value-finders]
            [viasat.cwmp-cpe.util.xml :as xml-util]))

;; A.3.2.1 SetParameterValues
(defn set-parameter-values->flat-map [spv]
  (reduce (fn [r parameter-value-struct]
            ;; XXX handle type transformations
            (let [{:keys [:Name :Value]}
                  (->> parameter-value-struct
                       :content
                       (reduce (fn set-parameter-values->flat-map* [r2 {:keys [tag attrs content]}]
                                 (let [content (first content)
                                       txfn (-> attrs :xsi:type tr069-type-transforms/xsi-transform-from)
                                       content (if-not txfn content (txfn content))]
                                   (assoc r2 tag content)))
                               {}))]
              (assoc r Name Value)))
          {}
          spv))

(defn acs-message->rpc-method
  "Given a message from the ACS like {:status 123, :body <clojure-data>}, find and return the RPC method as a keyword."
  [{:keys [body]}]
  ;; XXX handle casing of the SOAP-ENV XML namespace
  (when-let [soap-body (value-finders/find-value (xml-util/xml-tag-finder-fn :SOAP-ENV:Body)
                                                 body)]
    (let [[rpc & unhandled] (->> soap-body :content (mapv :tag))]
      (when unhandled
        (throw (ex-info (format "Multiple rpcs currently unhandled. Found good %s, but also unexpected: %s"
                                rpc
                                unhandled)
                        {:cause :cannot-identify-rpc
                         :rpc rpc
                         :unhandled unhandled})))
      rpc)))

(defn acs-message->msg-id
  "Given a message from the ACS like {:status 123, :body <clojure-data>}, find and return the TR-069 message ID."
  [{:keys [body]}]
  ;; XXX handle casing of the SOAP-ENV XML namespace
  (when-let [soap-header (value-finders/find-value (xml-util/xml-tag-finder-fn :SOAP-ENV:Header)
                                                   body)]
    ;; XXX handle casing of the cwmp XML namespace
    (let [cwmp-id-node (value-finders/find-value (xml-util/xml-tag-finder-fn :cwmp:ID)
                                                 soap-header)]
      (first (:content cwmp-id-node)))))

;; ALSO handle empty body to end session
(defmulti handle-acs-message
  "TR-069 ACS message handler
  See A.3, and A.3.2 in the TR-069 Amendment 6 spec for RPC methods."
  (fn [stateful-device {:as acs-message :keys [status]}]
    (log/trace :handle-acs-message-dispatch
               (stateful-device/get-serial-number stateful-device)
               acs-message)
    (cond
      ;; XXX handle TR-069 errors, which are HTTP 200s and in the body
      (= 200 status) (acs-message->rpc-method acs-message)
      (= 204 status) ::session-end-offer
      (<= 500 status) (throw (ex-info (format "HTTP status from ACS is %s" status)
                                      {:cause :http-status-error-from-acs
                                       :status status
                                       :acs-message acs-message})))))

(defn log-handler-event [stateful-device acs-message rpc-method]
  (log/trace :handle-acs-message
             (stateful-device/get-serial-number stateful-device)
             rpc-method
             acs-message))

(defmethod handle-acs-message ::session-end-offer
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message ::session-end-offer)
  {:session-end-offer? true})

(defmethod handle-acs-message :cwmp:InformResponse
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message :cwmp:InformResponse)
  nil)

(defmethod handle-acs-message :cwmp:FactoryReset
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message :cwmp:FactoryReset)
  (let [msg-id (acs-message->msg-id acs-message)]
    (messages/tr069-factory-reset-response msg-id)))

(defmethod handle-acs-message :cwmp:SetParameterValues
  ;; A.3.2.1 SetParameterValues
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message :cwmp:SetParameterValues)
  (let [spvs (:content
              (value-finders/find-value (xml-util/xml-tag-finder-fn :ParameterList)
                                        acs-message))
        kvs (set-parameter-values->flat-map spvs)
        msg-id (acs-message->msg-id acs-message)
        spv-result (stateful-device/acs-set-parameter-values! stateful-device kvs)]
    ;; XXX handle/introduce errors... a general TODO
    (if-not (anomaly? spv-result)
      (messages/tr069-set-parameter-values-response msg-id "0")
      (do
        (log/error spv-result)
        (messages/tr069-fault-response msg-id spv-result)))))

(defmethod handle-acs-message :cwmp:GetParameterValues
  ;; A.3.2.2 GetParameterValues
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message :cwmp:GetParameterValues)
  (let [param-name-patterns (->> (value-finders/find-value (xml-util/xml-tag-finder-fn :ParameterNames)
                                                           acs-message)
                                 :content
                                 (mapv (comp first :content)))
        msg-id (acs-message->msg-id acs-message)
        param-kvs (stateful-device/get-parameter-values stateful-device param-name-patterns)]
    ;; XXX handle errors
    (messages/tr069-get-parameter-values-response msg-id {:ParameterList param-kvs})))

(defmethod handle-acs-message :cwmp:GetParameterNames
  ;; A.3.2.3 GetParameterNames
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message :cwmp:GetParameterNames)
  (let [msg-id (acs-message->msg-id acs-message)
        param-path (xml-util/xml->tag-content acs-message :ParameterPath)
        param-names (stateful-device/get-parameter-names stateful-device param-path)]
    (messages/tr069-get-parameter-names-response msg-id {:ParameterList param-names})))

(defmethod handle-acs-message :cwmp:Download
  ;; A.3.2.8 Download
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message :cwmp:Download)
  (let [msg-id (acs-message->msg-id acs-message)]
    ;; intentionally a NOP; could do something here, depending what we want to test
    (messages/tr069-download-response msg-id {})))

(defmethod handle-acs-message :cwmp:AddObject
  ;; A.3.2.6 AddObject
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message :cwmp:AddObject)
  (let [msg-id (acs-message->msg-id acs-message)
        object-name (xml-util/xml->tag-content acs-message :ObjectName)
        parameter-key (xml-util/xml->tag-content acs-message :ParameterKey)
        {:as add-object-result :keys [instance-number]} (stateful-device/acs-add-object! stateful-device
                                                                                         object-name
                                                                                         parameter-key)]
    (if-not (anomaly? add-object-result)
      (messages/tr069-add-object-response msg-id {:InstanceNumber instance-number})
      (do
        (log/error add-object-result)
        (messages/tr069-fault-response msg-id add-object-result)))))

; Return fault if TR-069 event is not recognized
(defmethod handle-acs-message :default
  [stateful-device acs-message]
  (log-handler-event stateful-device acs-message ::default)
  (throw (ex-info (format "dun blowed up trying to handle-acs-message for device %s, summary %s, acs-message: %s"
                          stateful-device (summarize-util/summarize-tr069-message acs-message) acs-message)
                  {:cause :unhandled-acs-message
                   :stateful-device stateful-device
                   :acs-message acs-message}))
  #_(responses/soap-fault session :method-not-supported (format "Unknown or unsupported event: %s" (:name event))))
