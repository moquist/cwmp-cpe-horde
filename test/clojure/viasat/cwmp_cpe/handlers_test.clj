(ns viasat.cwmp-cpe.handlers-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.test]
            [viasat.cwmp-cpe.handlers :as handlers]
            [viasat.cwmp-cpe.stateful-device.atom :as stateful-device-atom]
            [viasat.cwmp-cpe.util.value-finders :as value-finders]
            [viasat.cwmp-cpe.util.xml :as xml-util]))

(deftest acs-message->rpc-method-test
  (let [test-cases [{:description "FactoryReset"
                     :acs-message
                     {:tag :SOAP-ENV:Envelope,
                      :content
                      [{:tag :SOAP-ENV:Header,
                        :attrs nil,
                        :content
                        [{:tag :cwmp:ID,
                          :attrs {:SOAP-ENV:mustUnderstand "1"},
                          :content ["FR-1-1736199904.426175"]}]}
                       {:tag :SOAP-ENV:Body,
                        :attrs nil,
                        :content [{:tag :cwmp:FactoryReset, :attrs nil, :content nil}]}]}
                     :expected :cwmp:FactoryReset}]]
    (doseq [{:keys [description acs-message expected]} test-cases]
      (testing description
        (is (= expected
               (handlers/acs-message->rpc-method {:body acs-message})))))))

(deftest handle-acs-message-test
  (let [test-cases [{:description "inform-response"
                     :acs-message
                     {:status 200
                      :body {:tag :SOAP-ENV:Envelope,
                             :content
                             [{:tag :SOAP-ENV:Body,
                               :content [{:tag :cwmp:InformResponse}]}]}}
                     :expected-match nil?}
                    {:description "factory-reset"
                     :acs-message
                     {:status 200
                      :body {:tag :SOAP-ENV:Envelope,
                             :content
                             [{:tag :SOAP-ENV:Header,
                               :attrs nil,
                               :content
                               [{:tag :cwmp:ID,
                                 :attrs {:SOAP-ENV:mustUnderstand "1"},
                                 :content ["FR-1-1736199904.426175"]}]}
                              {:tag :SOAP-ENV:Body,
                               :attrs nil,
                               :content [{:tag :cwmp:FactoryReset, :attrs nil, :content nil}]}]}}
                     :expected-match {:msg-id string?
                                      :message
                                      [:SOAP-ENV:Envelope {}
                                       [:SOAP-ENV:Header nil
                                        [:cwmp:ID {} string?]]
                                       [:SOAP-ENV:Body nil
                                        [:cwmp:FactoryResetResponse]]]}}]]
    (doseq [{:keys [description acs-message expected-match]} test-cases
            :let [stateful-device (stateful-device-atom/stateful-device-atom
                                   "fefefe012345"
                                   {}
                                   {:acs-url "fake-acs-url"})]]
      (testing description
        (is (match? expected-match
                    (handlers/handle-acs-message stateful-device acs-message)))))))

(deftest handle-acs-message-get-parameter-names-test
  (let [all-params-expected-match {:msg-id string?
                                   :message
                                   [:SOAP-ENV:Envelope
                                    {:xmlns:SOAP-ENV "http://schemas.xmlsoap.org/soap/envelope/",
                                     :xmlns:SOAP-ENC "http://schemas.xmlsoap.org/soap/encoding/",
                                     :xmlns:xsd "http://www.w3.org/2001/XMLSchema",
                                     :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance",
                                     :xmlns:cwmp "urn:dslforum-org:cwmp-1-2"}
                                    [:SOAP-ENV:Header
                                     nil
                                     [:cwmp:ID {:SOAP-ENV:mustUnderstand "1"} string?]]
                                    [:SOAP-ENV:Body
                                     nil
                                     [:cwmp:GetParameterNamesResponse
                                      nil
                                      (matchers/all-of
                                       (matchers/prefix [:ParameterList
                                                         {:SOAP-ENC:arrayType #"cwmp:ParameterInfoStruct\[[0-9]+\]"}])
                                       (matchers/via (partial drop 2)
                                                     (matchers/seq-of
                                                      [:ParameterInfoStruct
                                                       nil
                                                       [:Name nil string?]
                                                       [:Writable nil boolean?]])))]]]}
        test-cases [{:description "get-parameter-names with nil path"
                     :parameter-path nil
                     :expected-match all-params-expected-match}
                    {:description "get-parameter-names with empty-string path"
                     :parameter-path ""
                     :expected-match all-params-expected-match}
                    {:description "get-parameter-names with empty-string path"
                     :parameter-path [""]
                     :expected-match all-params-expected-match}
                    {:description "get-parameter-names with path: Device."
                     :parameter-path ["Device."]
                     :expected-match all-params-expected-match}
                    {:description "get-parameter-names with path: Device.ManagementServer."
                     :parameter-path ["Device.ManagementServer.InstanceWildcardsSupported"]
                     :expected-match all-params-expected-match
                     :expected-params-returned ["Device.ManagementServer.InstanceWildcardsSupported"]}
                    {:description "get-parameter-names with path: Device.ManagementServer."
                     :parameter-path ["Device.ManagementServer."]
                     :expected-match all-params-expected-match
                     :expected-params-returned ["Device.ManagementServer.InstanceWildcardsSupported"]}
                    {:description "get-parameter-names with non-matching path"
                     :parameter-path ["probably should not match anything"]
                     :expected-match nil
                     :expected-params-returned []}]]

;       [:ParameterList
;             {:SOAP-ENC:arrayType "cwmp:ParameterInfoStruct[7]"}
;             [:ParameterInfoStruct
;              nil
;              [:Name nil "Device.DeviceInfo.ManufacturerOUI"]
;              [:Writable nil true]]
;             [:ParameterInfoStruct
;              nil
;              [:Name nil "Device.DeviceInfo.HardwareVersion"]
;              [:Writable nil true]]
;             [:ParameterInfoStruct
;              nil
;              [:Name nil "Device.DeviceInfo.Manufacturer"]
;              [:Writable nil true]]
;             [:ParameterInfoStruct
;              nil
;              [:Name nil "Device.DeviceInfo.SerialNumber"]
;              [:Writable nil true]]
;             [:ParameterInfoStruct
;              nil
;              [:Name nil "Device.DeviceInfo.SoftwareVersion"]
;              [:Writable nil true]]
;             [:ParameterInfoStruct
;              nil
;              [:Name
;               nil
;               "Device.ManagementServer.InstanceWildcardsSupported"]
;              [:Writable nil true]]
;             [:ParameterInfoStruct
;              nil
;              [:Name nil "Device.DeviceInfo.ProductClass"]
;              [:Writable nil true]]]
    (doseq [{:keys [description parameter-path expected-match expected-params-returned]} test-cases
            :let [stateful-device (stateful-device-atom/stateful-device-atom
                                   "fefefe012345"
                                   {}
                                   {:acs-url "fake-acs-url"})
                  acs-message {:status 200,
                               :body
                               {:tag :soap-env:Envelope,
                                :attrs
                                {:xmlns:cwmp "urn:dslforum-org:cwmp-1-2",
                                 :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance",
                                 :xmlns:xsd "http://www.w3.org/2001/XMLSchema",
                                 :xmlns:soap-env "http://schemas.xmlsoap.org/soap/envelope/",
                                 :xmlns:soap-enc "http://schemas.xmlsoap.org/soap/encoding/"},
                                :content
                                [{:tag :soap-env:Header,
                                  :attrs nil,
                                  :content
                                  [{:tag :cwmp:ID,
                                    :attrs {:soap-env:mustUnderstand "1"},
                                    :content ["19731146ec00000"]}]}
                                 {:tag :soap-env:Body,
                                  :attrs nil,
                                  :content
                                  [{:tag :cwmp:GetParameterNames,
                                    :attrs nil,
                                    :content
                                    [{:tag :ParameterPath, :attrs nil, :content parameter-path}
                                     {:tag :NextLevel, :attrs nil, :content ["1"]}]}]}]}}
                  result (handlers/handle-acs-message stateful-device acs-message)]]
      (testing description
        (when expected-match
          (is (match? expected-match result)))
        (when expected-params-returned
          (is (= expected-params-returned
                 (->> (-> result :message xml-util/xml->map-xml)
                      (value-finders/collect-values (xml-util/xml-tag-finder-fn :ParameterInfoStruct))
                      (value-finders/collect-values (xml-util/xml-tag-finder-fn :Name))
                      (mapv (comp first :content))))))))))

(deftest set-parameter-values->flat-map-test
  (is (= {"Device.ManagementServer.ConnectionRequestPassword" "tF8jVP8XxpRn",
          "Device.DeviceInfo.ProvisioningCode" "DefaultProvisioningCode",
          "Device.ManagementServer.PeriodicInformEnable" true,
          "Device.ManagementServer.PeriodicInformInterval" 86400,
          "Device.ManagementServer.ConnectionRequestUsername" "niym2s2mhONZ",
          "Device.ManagementServer.PeriodicInformTime" #inst "1970-01-01T04:24:10.000-00:00"}
         (handlers/set-parameter-values->flat-map
          [{:tag :ParameterValueStruct,
            :content [{:tag :Name, :attrs nil, :content ["Device.ManagementServer.ConnectionRequestPassword"]}
                      {:tag :Value, :attrs {:xsi:type "xsd:string"}, :content ["tF8jVP8XxpRn"]}]}
           {:tag :ParameterValueStruct,
            :content [{:tag :Name, :attrs nil, :content ["Device.DeviceInfo.ProvisioningCode"]}
                      {:tag :Value, :attrs {:xsi:type "xsd:string"}, :content ["DefaultProvisioningCode"]}]}
           {:tag :ParameterValueStruct,
            :content [{:tag :Name, :attrs nil, :content ["Device.ManagementServer.PeriodicInformEnable"]}
                      {:tag :Value, :attrs {:xsi:type "xsd:boolean"}, :content ["1"]}]}
           {:tag :ParameterValueStruct,
            :content [{:tag :Name, :attrs nil, :content ["Device.ManagementServer.PeriodicInformInterval"]}
                      {:tag :Value, :attrs {:xsi:type "xsd:unsignedInt"}, :content ["86400"]}]}
           {:tag :ParameterValueStruct,
            :content [{:tag :Name, :attrs nil, :content ["Device.ManagementServer.ConnectionRequestUsername"]}
                      {:tag :Value, :attrs {:xsi:type "xsd:string"}, :content ["niym2s2mhONZ"]}]}
           {:tag :ParameterValueStruct,
            :content [{:tag :Name, :attrs nil, :content ["Device.ManagementServer.PeriodicInformTime"]}
                      {:tag :Value, :attrs {:xsi:type "xsd:dateTime"}, :content ["1970-01-01T04:24:10Z"]}]}]))))
