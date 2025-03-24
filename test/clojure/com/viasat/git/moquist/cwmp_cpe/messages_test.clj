(ns com.viasat.git.moquist.cwmp-cpe.messages-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.viasat.git.moquist.cwmp-cpe.messages :as messages]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test]))

(def DeviceIdStruct-base
  "DeviceId as defined in the TR-069 standard:
  \"A structure that uniquely identifies the CPE.\"
  See A.3.3.1 and Table 39 in TR-069_Amendment-6.pdf"
  {:Manufacturer "manufacturer"
   :ProductClass "product-class"
   :SerialNumber "serial-number"
   :OUI "FEFEFE"})

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

(deftest map->hiccup-xml-tags-test
  (is (= [["hi" nil 1]
          ["there" nil 2]
          [3 nil 7]]
         (messages/map->hiccup-xml-tags {:hi 1
                                         "there" 2
                                         3 7}))))

(deftest soap-envelope-test
  (is (= [:SOAP-ENV:Envelope
          {:xmlns:SOAP-ENV "http://schemas.xmlsoap.org/soap/envelope/",
           :xmlns:SOAP-ENC "http://schemas.xmlsoap.org/soap/encoding/",
           :xmlns:xsd "http://www.w3.org/2001/XMLSchema",
           :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance",
           :xmlns:cwmp "urn:dslforum-org:cwmp-1-2"}
          [:SOAP-ENV:Header
           nil
           [:cwmp:ID {:SOAP-ENV:mustUnderstand "1"} "hello"]]
          [:SOAP-ENV:Body
           nil
           [:sometag nil :subtag1 {:a 1} "subtag1 value"]]]
         (messages/soap-envelope "hello" [:sometag nil
                                          :subtag1 {:a 1} "subtag1 value"]))))

(deftest event->eventstruct-test
  (is (= [[:EventStruct
           nil
           [:EventCode nil "0 BOOTSTRAP"]
           [:CommandKey]]
          [:EventStruct
           nil
           [:EventCode nil "1 BOOT"]
           [:CommandKey]]]
         (messages/event->eventstruct ["0 BOOTSTRAP" "1 BOOT"]))))

(deftest tr069-inform-test
  (let [device-id (:SerialNumber DeviceIdStruct-base)
        test-cases [{:description "bootstrap-boot, basic TR-069 inform compliance"
                     :tr069-data {:DeviceId DeviceIdStruct-base
                                  :ParameterList ParameterList-base
                                  :Event ["0 BOOTSTRAP" "1 BOOT"]}
                     :expected-match {:msg-id string?
                                      :inform-body
                                      [:SOAP-ENV:Envelope
                                       {}
                                       [:SOAP-ENV:Header
                                        nil
                                        [:cwmp:ID
                                         {}
                                         string?]]
                                       [:SOAP-ENV:Body
                                        nil
                                        [:cwmp:Inform
                                         nil
                                         [:DeviceId
                                          nil
                                          ["Manufacturer" nil string?]
                                          ["ProductClass" nil string?]
                                          ["SerialNumber" nil string?]
                                          ["OUI" nil string?]]
                                         [:Event
                                          {:SOAP-ENC:arrayType #"EventStruct\[[0-9]+\]"}
                                          [:EventStruct
                                           nil
                                           [:EventCode nil "0 BOOTSTRAP"]
                                           [:CommandKey]]
                                          [:EventStruct
                                           nil
                                           [:EventCode nil "1 BOOT"]
                                           [:CommandKey]]]
                                         (m/prefix
                                          [:ParameterList
                                           {:SOAP-ENC:arrayType #"ParameterValueStruct\[[0-9]+\]"}
                                            ;; match at least one :ParameterValueStruct
                                           [:ParameterValueStruct
                                            nil
                                            [:Name nil string?]
                                            [:Value {:xsi:type string?} any?]]])
                                         [:MaxEnvelopes nil "1"]
                                         [:CurrentTime nil string?]
                                         [:RetryCount nil 0]]]]}}]]
    (doseq [{:keys [description tr069-data expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (messages/tr069-inform device-id tr069-data)))))))

(deftest tr069-get-parameter-values-response-test
  (let [test-cases [{:description "basic TR-069 compliance"
                     :tr069-data {:ParameterList ParameterList-base}
                     :expected-match {:msg-id string?
                                      :message
                                      [:SOAP-ENV:Envelope
                                       {}
                                       [:SOAP-ENV:Header
                                        nil
                                        [:cwmp:ID
                                         {}
                                         string?]]
                                       [:SOAP-ENV:Body
                                        nil
                                        [:cwmp:GetParameterValuesResponse
                                         nil
                                         (m/prefix
                                          [:ParameterList
                                           {:SOAP-ENC:arrayType #"ParameterValueStruct\[[0-9]+\]"}
                                            ;; match at least one :ParameterValueStruct
                                           [:ParameterValueStruct
                                            nil
                                            [:Name nil string?]
                                            [:Value {:xsi:type string?} any?]]])]]]}}]]
    (doseq [{:keys [description tr069-data expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (messages/tr069-get-parameter-values-response "msg-id-123" tr069-data)))))))

(deftest tr069-download-response-test
  (let [test-cases [{:description "basic TR-069 compliance"
                     :tr069-data {:Status 0
                                  :StartTime "2025-01-03T20:56:39+00:00"}
                     :expected-match {:msg-id string?
                                      :message
                                      [:SOAP-ENV:Envelope
                                       {}
                                       [:SOAP-ENV:Header
                                        nil
                                        [:cwmp:ID
                                         {}
                                         string?]]
                                       [:SOAP-ENV:Body
                                        nil
                                        [:cwmp:DownloadResponse
                                         nil
                                         [:Status nil 0]
                                         [:StartTime nil "2025-01-03T20:56:39+00:00"]
                                         [:CompletedTime nil string?]]]]}}]]
    (doseq [{:keys [description tr069-data expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (messages/tr069-download-response "msg-id-123" tr069-data)))))))

(deftest tr069-factory-reset-response-test
  (let [test-cases [{:description "basic TR-069 compliance"
                     :expected-match {:msg-id string?
                                      :message
                                      [:SOAP-ENV:Envelope
                                       {}
                                       [:SOAP-ENV:Header
                                        nil
                                        [:cwmp:ID
                                         {}
                                         string?]]
                                       [:SOAP-ENV:Body
                                        nil
                                        [:cwmp:FactoryResetResponse]]]}}]]
    (doseq [{:keys [description expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (messages/tr069-factory-reset-response "msg-id-123")))))))

(deftest tr069-set-parameter-values-response-test
  (let [test-cases [{:description "basic TR-069 compliance"
                     :spv-status "0"
                     :expected-match {:msg-id string?
                                      :message
                                      [:SOAP-ENV:Envelope
                                       {}
                                       [:SOAP-ENV:Header
                                        nil
                                        [:cwmp:ID
                                         {}
                                         string?]]
                                       [:SOAP-ENV:Body
                                        nil
                                        [:cwmp:SetParameterValuesResponse
                                         nil
                                         [:Status nil "0"]]]]}}]]
    (doseq [{:keys [description spv-status expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (messages/tr069-set-parameter-values-response "msg-id-123" spv-status)))))))

(deftest tr069-fault-response-test
  (let [test-cases [{:description "basic TR-069 compliance"
                     :fault-elements {:soap-fault-code "Client"
                                      :soap-fault-string "CWMP fault"
                                      :cwmp-fault-code "9003"
                                      :cwmp-fault-string "Invalid arguments"}
                     :expected-match {:msg-id string?
                                      :message
                                      [:SOAP-ENV:Envelope
                                       {}
                                       [:SOAP-ENV:Header
                                        nil
                                        [:cwmp:ID
                                         {}
                                         string?]]
                                       [:SOAP-ENV:Body
                                        nil
                                        [:SOAP-ENV:Fault
                                         nil
                                         [:faultcode nil "Client"]
                                         [:faultstring nil "CWMP fault"]
                                         [:detail
                                          nil
                                          [:cwmp:Fault
                                           nil
                                           [:FaultCode nil "9003"]
                                           [:FaultString nil "Invalid arguments"]]]]]]}}]]
    (doseq [{:keys [description fault-elements expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (messages/tr069-fault-response "msg-id-123" fault-elements)))))))
