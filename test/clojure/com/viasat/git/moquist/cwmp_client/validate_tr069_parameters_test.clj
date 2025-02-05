(ns com.viasat.git.moquist.cwmp-client.validate-tr069-parameters-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.viasat.git.moquist.cwmp-client.validate-tr069-parameters :as validate-parameters]
            [matcher-combinators.test]))

(set! *warn-on-reflection* true)

(defmethod validate-parameters/validate-parameter "some-param"
  [[_k ^java.lang.String v]]
  (or (and (string? v)
           (.contains v "monkey"))
      {:cognitect.anomalies/category :cognitect.anomalies/incorrect
       :message "some-param parameter value invalid; must contain \"monkey\""}))

(deftest validate-tr069-parameters-test
  (let [test-cases [{:description "nil case"
                     :kvs nil
                     :expected-match true}
                    {:description "empty case"
                     :kvs {}
                     :expected-match true}
                    {:description "default case"
                     :kvs {:a 1
                           "bee" 2}
                     :expected-match true}
                    {:description "basic validity"
                     :kvs {"Device.DeviceInfo.SoftwareVersion" "1.2.3"}
                     :expected-match true}
                    {:description "basic invalidity"
                     :kvs {"Device.DeviceInfo.SoftwareVersion" 1.2}
                     :expected-match {:cognitect.anomalies/category :cognitect.anomalies/incorrect}}
                    {:description "custom valid case"
                     :kvs {:a 1
                           "some-param" "favorite monkey tree"}
                     :expected-match true}
                    {:description "custom invalid case"
                     :kvs {:a 1
                           "some-param" "favorite banana tree"}
                     :expected-match {:cognitect.anomalies/category :cognitect.anomalies/incorrect}}]]
    (doseq [{:keys [description kvs expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (validate-parameters/validate-parameters kvs)))))))
