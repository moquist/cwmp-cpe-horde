(ns viasat.cwmp-cpe.messages
  "Written according to TR-069 Amendment 6"
  (:require [clojure.data.xml :as xml]
            [viasat.cwmp-cpe.tr069-type-transforms :as tr069-type-transforms]
            [viasat.cwmp-cpe.util.time :as time-util]
            [viasat.cwmp-cpe.util.tr069 :as tr069-util]))

(set! *warn-on-reflection* true)

(defn keyword->str [v]
  (if (keyword? v)
    (name v)
    v))

(defn parameter-value-structs
  [params]
  (mapv #(let [{:keys [cwmp-name value xsi-type]} (tr069-type-transforms/to-xsi %)]
           [:ParameterValueStruct nil
            [:Name nil (keyword->str cwmp-name)]
            [:Value {:xsi:type xsi-type} value]])
        params))

(defn parameter-info-structs
  [params]
  (mapv #(let [{:keys [cwmp-name]} (tr069-type-transforms/to-xsi %)]
           [:ParameterInfoStruct nil
            [:Name nil (keyword->str cwmp-name)]
            ;; TODO: support some non-writable params; useful to test
            [:Writable nil true]])
        params))

;; XXX allow keywords for keys? vals? neither?
(defn map->hiccup-xml-tags
  "Transform a map (or seq of pairs) into hiccup-style xml tags"
  [tags]
  (mapv (fn [[k v]]
          [(keyword->str k) nil (keyword->str v)])
        tags))

(defn soap-envelope [msg-id soap-body-content]
  [:SOAP-ENV:Envelope {:xmlns:SOAP-ENV "http://schemas.xmlsoap.org/soap/envelope/"
                       :xmlns:SOAP-ENC "http://schemas.xmlsoap.org/soap/encoding/"
                       :xmlns:xsd "http://www.w3.org/2001/XMLSchema"
                       :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
                       :xmlns:cwmp "urn:dslforum-org:cwmp-1-2"}
   [:SOAP-ENV:Header nil
    [:cwmp:ID {:SOAP-ENV:mustUnderstand "1"} msg-id]]
   [:SOAP-ENV:Body nil soap-body-content]])

(defn event->eventstruct
  "Given 0 to many events, return a seq of TR-069 EventStruct tags in hiccup-style xml"
  [events]
  (mapv (fn [event]
          [:EventStruct nil
           [:EventCode nil event]
           ;; TODO: CommandKey must have content in specific circumstances documented in Table 40.
           ;; See example in pcap 03: 7 TRANSFER COMPLETE
           [:CommandKey]])
        events))

(defn tr069-inform
  "TR-069 A.3.3.1 Inform
  Given a device-id and optional tr-069 data, return a hiccup-style xml structure representing a TR-069 inform message."
  [device-id
   {:as _tr069-data
    ;; DeviceId as defined in the TR-069 standard:
    ;; "A structure that uniquely identifies the CPE."
    ;; See A.3.3.1 and Table 39 in TR-069_Amendment-6.pdf
    :keys [DeviceId Event CurrentTime RetryCount ParameterList]}]
  (let [msg-id (tr069-util/generate-msg-id "Inform" device-id)
        CurrentTime (or CurrentTime (time-util/format-datetime (time-util/now)))
        RetryCount (or RetryCount 0)
        device-id-content (map->hiccup-xml-tags DeviceId)
        event-content (event->eventstruct Event)
        parameterlist-content (parameter-value-structs ParameterList)]
    {:msg-id msg-id
     :inform-body (soap-envelope
                   msg-id
                   [:cwmp:Inform nil
                    (into [:DeviceId nil] device-id-content)
                    (into
                     [:Event
                      {:SOAP-ENC:arrayType (format "cwmp:EventStruct[%d]"
                                                   (count event-content))}]
                     event-content)
                    (into
                     [:ParameterList
                      {:SOAP-ENC:arrayType (format "cwmp:ParameterValueStruct[%d]"
                                                   (count parameterlist-content))}]
                     parameterlist-content)
                       ;; Table 37, p. 107
                       ;; "[MaxEnvelopes] MUST be set to a value of 1 because this version of the
                       ;; protocol supports only a single envelope per message, and on reception
                       ;; its value MUST be ignored."
                    [:MaxEnvelopes nil "1"]
                    [:CurrentTime nil CurrentTime]
                    [:RetryCount nil RetryCount]])}))

(defn tr069-get-parameter-values-response
  "TR-069 A.3.2.2 GetParameterValues"
  [msg-id {:as _tr069-data :keys [ParameterList]}]
  (let [parameterlist-content (parameter-value-structs ParameterList)]
    {:msg-id msg-id
     :message (soap-envelope
               msg-id
               [:cwmp:GetParameterValuesResponse nil
                (into
                 [:ParameterList
                  {:SOAP-ENC:arrayType (format "cwmp:ParameterValueStruct[%d]"
                                               (count parameterlist-content))}]
                 parameterlist-content)])}))

(defn tr069-get-parameter-names-response
  "TR-069 A.3.2.3 GetParameterNames"
  [msg-id {:as _tr069-data :keys [ParameterList]}]
  (let [parameterlist-content (parameter-info-structs ParameterList)]
    {:msg-id msg-id
     :message (soap-envelope
               msg-id
               [:cwmp:GetParameterNamesResponse nil
                (into
                 [:ParameterList
                  {:SOAP-ENC:arrayType (format "cwmp:ParameterInfoStruct[%d]"
                                               (count parameterlist-content))}]
                 parameterlist-content)])}))

(defn tr069-add-object-response
  "TR-069 A.3.2.6 AddObject"
  [msg-id {:as _tr069-data :keys [InstanceNumber]}]
  {:msg-id msg-id
   :message (soap-envelope
             msg-id
             [:cwmp:AddObjectResponse
              nil
              [:InstanceNumber nil InstanceNumber]
              [:Status nil 0]])})

(comment
  (xml/indent-str (xml/sexp-as-element (:message (tr069-download-response "FEFEFE123456" {})))))

(defn tr069-download-response
  "TR-069 A.3.2.8 Download"
  [msg-id {:as _tr069-data
           :keys [Status StartTime CompletedTime]}]
  (let [Status (or Status 0)
        StartTime (or StartTime (time-util/format-datetime (time-util/now)))
        CompletedTime (or CompletedTime (time-util/format-datetime (time-util/now)))]
    {:msg-id msg-id
     :message (soap-envelope
               msg-id
               [:cwmp:DownloadResponse nil
                [:Status nil Status]
                [:StartTime nil StartTime]
                [:CompletedTime nil CompletedTime]])}))

(defn tr069-factory-reset-response
  "TR-069 A.4.1.6 FactoryReset"
  [msg-id]
  {:msg-id msg-id
   :message (soap-envelope msg-id [:cwmp:FactoryResetResponse])})

(defn tr069-set-parameter-values-response
  "TR-069 A.3.2.1 SetParameterValues"
  [msg-id spv-status]
  {:msg-id msg-id
   :message (soap-envelope msg-id [:cwmp:SetParameterValuesResponse
                                   nil [:Status nil spv-status]])})

;; XXX see "A fault response MUST make use of the SOAP Fault element ..." on p. 50
(defn tr069-fault-response
  "3.5 Use of SOAP, see p. 50-52.
  A.5 Fault Handling, p. 136.
  Other references appear throughout the spec."
  [msg-id {:keys [soap-fault-code
                  soap-fault-string
                  cwmp-fault-code
                  cwmp-fault-string]}]
  ;; TODO: something better than inline assertions
  (assert cwmp-fault-code)
  (assert cwmp-fault-string)
  {:msg-id msg-id
   :message (soap-envelope
             msg-id
             [:SOAP-ENV:Fault
              nil
              [:faultcode nil (or soap-fault-code "Client")]
              [:faultstring nil (or soap-fault-string "CWMP fault")]
              [:detail
               nil
               [:cwmp:Fault
                nil
                [:FaultCode nil cwmp-fault-code]
                [:FaultString nil cwmp-fault-string]]]])})

