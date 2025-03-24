(ns com.viasat.git.moquist.cwmp-cpe.handlers-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.viasat.git.moquist.cwmp-cpe.handlers :as handlers]
            [matcher-combinators.test]))

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
    (doseq [{:keys [description device-id acs-message expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (handlers/handle-acs-message (or device-id "")
                                                 acs-message)))))))

(deftest set-parameter-values->flat-map-test
  (is (= {"Device.ManagementServer.ConnectionRequestPassword" "tF8jVP8XxpRn",
          "Device.DeviceInfo.ProvisioningCode" "ViaSat_Default",
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
                      {:tag :Value, :attrs {:xsi:type "xsd:string"}, :content ["ViaSat_Default"]}]}
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
