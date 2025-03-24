(ns com.viasat.git.moquist.cwmp-cpe.util.tr069.summarize-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.viasat.git.moquist.cwmp-cpe.util.tr069.summarize :as summarize-util]
            [matcher-combinators.test]))

(deftest summarize-rpc-details-test
  (let [use-cases [{:description "soap-env:Fault"
                    :message {:tag :SOAP-ENV:Envelope,
                              :content [{:tag :SOAP-ENV:Body,
                                         :content [{:tag :SOAP-ENV:Fault,
                                                    :content [{:tag :faultcode, :content ["Client"]}
                                                              {:tag :faultstring, :content ["CWMP fault"]}
                                                              {:tag :detail,
                                                               :content [{:tag :cwmp:Fault,
                                                                          :content [{:tag :FaultCode,
                                                                                     :content ["9005"]}
                                                                                    {:tag :FaultString,
                                                                                     :content ["Invalid parameter name"]}]}]}]}]}]}
                    :rpc-verb :soap-env:Fault
                    :expected-match ["origin: Client" "9005" "Invalid parameter name"]}
                   {:description "cwmp:GetParameterValuesResponse"
                    :message {:tag :SOAP-ENV:Body,
                              :content [{:tag :cwmp:GetParameterValuesResponse,
                                         :content [{:tag :ParameterList,
                                                    :attrs {:SOAP-ENC:arrayType "cwmp:ParameterValueStruct[1]"},
                                                    :content [{:tag :ParameterValueStruct,
                                                               :content [{:tag :Name,
                                                                          :content ["Device.ManagementServer.ConnectionRequestUsername"]}
                                                                         {:tag :Value,
                                                                          :attrs {:xsi:type "xsd:string"},
                                                                          :content ["moquist-cnr-username"]}]}]}]}]}
                    :rpc-verb :cwmp:GetParameterValuesResponse
                    :expected-match {"Device.ManagementServer.ConnectionRequestUsername" "moquist-cnr-username"}}]]
    (doseq [{:keys [description message rpc-verb expected-match]} use-cases]
      (testing description
        (is (match? expected-match
                    (summarize-util/summarize-rpc-details rpc-verb message)))))))


