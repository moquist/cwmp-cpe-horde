(ns viasat.cwmp-cpe.util.tr069.summarize
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [viasat.cwmp-cpe.util.value-finders :as value-finders]
            [viasat.cwmp-cpe.util.xml :as xml-util]))

(set! *warn-on-reflection* true)

(defn extract-parameter-value-structs-as-map
  "Given some XML-as-map, e.g., {:tag ..., :attrs {...} :content [...]}, extract all ParameterValueStructs and return a Clojure map."
  [xml-map]
  (->> (value-finders/collect-values (xml-util/xml-tag-finder-fn :ParameterValueStruct) xml-map)
       (mapv (fn [{name-and-value-nodes :content}]
               (->> name-and-value-nodes
                    (mapv (fn [{:keys [tag content]}]
                            [tag (first content)]))
                    (into {}))))
       (mapv (juxt :Name :Value))
       (into {})))

(defn extract-parameter-info-structs-as-map
  "Given some XML-as-map, e.g., {:tag ..., :attrs {...} :content [...]}, extract all ParameterInfoStructs and return a Clojure map."
  [xml-map]
  (->> (value-finders/collect-values (xml-util/xml-tag-finder-fn :ParameterInfoStruct) xml-map)
       (mapv (fn [{name-and-property-nodes :content}]
               (->> name-and-property-nodes
                    (mapv (fn [{:keys [tag content]}]
                            [tag (first content)]))
                    (into {}))))
       (mapv (juxt :Name :Value))
       (into {})))

(defmulti summarize-rpc-details
  (fn [rpc-verb _message] (xml-util/xml-tag->lowercased-namespace rpc-verb)))

(defmethod summarize-rpc-details :default
  [rpc-verb message]
  (throw (Exception. (format "summarize-rpc-details unimplemented for %s: %s" rpc-verb message))))

(defmethod summarize-rpc-details :cwmp:Inform
  [_rpc-verb message]
  (extract-parameter-value-structs-as-map message))

(defmethod summarize-rpc-details :cwmp:InformResponse [& _])

(defmethod summarize-rpc-details :cwmp:FactoryReset [& _])
(defmethod summarize-rpc-details :cwmp:FactoryResetResponse [& _])

(defmethod summarize-rpc-details :cwmp:GetParameterNames
  [_rpc-verb message]
  (->> (value-finders/collect-values (xml-util/xml-tag-finder-fn :ParameterPath) message)
       (mapv (fn [{[parameter-name] :content}] parameter-name))))

(defmethod summarize-rpc-details :cwmp:GetParameterNamesResponse
  [_rpc-verb message]
  (extract-parameter-info-structs-as-map message))

(defmethod summarize-rpc-details :cwmp:GetParameterValues
  [_rpc-verb message]
  (->> (value-finders/find-value (xml-util/xml-tag-finder-fn :ParameterNames) message)
       :content
       (mapv :content)))

(defmethod summarize-rpc-details :cwmp:GetParameterValuesResponse
  [_rpc-verb message]
  (extract-parameter-value-structs-as-map message))

(defmethod summarize-rpc-details :cwmp:SetParameterValues
  [_rpc-verb message]
  (->> (value-finders/collect-values (xml-util/xml-tag-finder-fn :Name) message)
       (mapv (fn [{[parameter-name] :content}] parameter-name))))

(defmethod summarize-rpc-details :cwmp:SetParameterValuesResponse [& _])

(defmethod summarize-rpc-details :cwmp:AddObject
  [_rpc-verb message]
  (xml-util/xml->tag-content message :ObjectName))

(defmethod summarize-rpc-details :cwmp:AddObjectResponse
  [_rpc-verb message]
  {:InstanceNumber (xml-util/xml->tag-content message :InstanceNumber)
   :Status (xml-util/xml->tag-content message :Status)})

(defmethod summarize-rpc-details :soap-env:Fault
  [_rpc-verb message]
  (let [{[soap-faultcode] :content} (value-finders/find-value (xml-util/xml-tag-finder-fn :faultcode) message)
        {[cwmp-fault-code] :content} (value-finders/find-value (xml-util/xml-tag-finder-fn :FaultCode) message)
        {[cwmp-fault-string] :content} (value-finders/find-value (xml-util/xml-tag-finder-fn :FaultString) message)]
    [(str "origin: " soap-faultcode) cwmp-fault-code cwmp-fault-string]))

(defn summarize-tr069-message
  "Return the heuristically most-helpful bits just to get an idea what kind of message it is.
  The helpful bits seem to be events (CPE->ACS), RPCs (ACS->CPE), and RPC responses (CPE->ACS)."
  [message]
  (when message
    (let [message (xml-util/xml->map-xml message)
          events (->> (value-finders/collect-values (xml-util/xml-tag-finder-fn :EventCode) message)
                      (mapv (comp first :content)))
          rpc-verb (:tag (xml-util/xml->tag-content message :SOAP-ENV:Body))
          rpc-summary-details (when rpc-verb (summarize-rpc-details rpc-verb message))]
      (cond-> nil
        (seq events) (assoc :events events)
        rpc-verb (assoc :rpc-verb rpc-verb)
        (seq rpc-summary-details) (assoc :rpc-summary-details rpc-summary-details)))))

(defn summarize-tr069-packet-file
  [summary-filename]
  (let [{:keys [packet-number direction http-body]} (edn/read-string (slurp summary-filename))
        message-summary (summarize-tr069-message http-body)]
    (cond-> {:direction direction
             :packet-number packet-number}
      message-summary (assoc :message-summary message-summary))))

(defn summarize-tr069-session
  "Given a directory containing files output by viasat.pcapper/parse-pcap, summarize the session in terms of directionality (request/response) and TR-069 message content."
  [dirpath]
  (->> (file-seq (io/file dirpath))
       (map (fn [^java.io.File x] (.getPath x)))
       (filter (fn [^java.lang.String x] (.contains x "summary.edn")))
       sort
       (mapv summarize-tr069-packet-file)))
