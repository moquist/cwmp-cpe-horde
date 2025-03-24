(ns com.viasat.git.moquist.cwmp-cpe.stateful-device.atom-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.viasat.git.moquist.cwmp-cpe.stateful-device :as stateful-device]
            [com.viasat.git.moquist.cwmp-cpe.stateful-device.atom :as stateful-device-atom]
            [com.viasat.git.moquist.cwmp-cpe.validate-tr069-parameters :as validate-parameters]
            [matcher-combinators.test]))

(set! *warn-on-reflection* true)

(defn spvs-valid? [{:as _spvs ^java.lang.String software-version "Device.DeviceInfo.SoftwareVersion"}]
  (if (or (not (string? software-version)) (.contains software-version "_"))
    true
    {:cognitect.anomalies/category :cognitect.anomalies/incorrect
     :message (format "Device.DeviceInfo.SoftwareVersion must contain \"_\" for this test. The following does not contain \"_\": %s"
                      software-version)
     :cwmp-fault-code "9007"
     :cwmp-fault-string "Invalid parameter value"}))

(deftest StatefulDeviceAtom-test
  ;; XXX is there a better way to do this? Defining a multimethod is stateful...
  (defmethod validate-parameters/validate-parameter "StatefulDeviceAtom-test-parameter"
    [[k v]]
    {:cognitect.anomalies/category :cognitect.anomalies/incorrect
     :parameter-name k
     :parameter-value v
     :message "this test is succeeding!"
     ::test-success? :yep})
  (let [use-cases [{:description "just initialization"
                    :initial-cpe-parameter-values {"hi" "ho"}
                    :steps []
                    :expected-match-state {:spvs {"hi" "ho"}
                                           :spvs-sources {"hi" :cpe}}}

                   {:description "initialization failure -- invalid SPV according to TR-181 spec"
                    :initial-cpe-parameter-values {"Device.DeviceInfo.Manufacturer"
                                                   (apply str (repeat 65 "a"))}
                    :steps []
                    :expected-match-device {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                                            :message string?}}

                   {:description "basic happy paths"
                    :steps [{:f stateful-device/cpe-set-parameter-values!
                             :args [{"reported-satellite-id" 78
                                     "oem" "Southrup Shrinkman, LLC"}]}
                            {:f stateful-device/acs-set-parameter-values!
                             :args [{"prescribed-satellite-id" 77}]}
                            {:f stateful-device/acs-set-parameter-values!
                             :args [{"voip-enabled" true}]}
                            {:f stateful-device/get-parameter-values
                             :args [["prescribed-satellite-id" "reported-satellite-id"]]}]
                    :expected-match-state {:spvs {"reported-satellite-id" 78
                                                  "oem" "Southrup Shrinkman, LLC"
                                                  "prescribed-satellite-id" 77
                                                  "voip-enabled" true}
                                           :spvs-sources {"reported-satellite-id" :cpe
                                                          "oem" :cpe
                                                          "prescribed-satellite-id" :acs
                                                          "voip-enabled" :acs}}
                    :expected-match-last-step {"reported-satellite-id" 78
                                               "prescribed-satellite-id" 77}}

                   {:description "wildcard paths"
                    :steps [{:f stateful-device/cpe-set-parameter-values!
                             :args [{"a.b.c" "abc"
                                     "a.b.c.from-cpe.e" "abc-from-cpee"
                                     "a.ziggy.c.from-cpe.e" "ab-ziggy-from-cpee"
                                     "a.b.crazy" "abcrazy"}]}
                            {:f stateful-device/acs-set-parameter-values!
                             :args [{"a.b.c.from-acs" "abc-from-acs"}]}
                            {:f stateful-device/get-parameter-values
                             :args [["a.*.c."]]}]
                    :expected-match-last-step {"a.b.c.from-cpe.e" "abc-from-cpee"
                                               "a.ziggy.c.from-cpe.e" "ab-ziggy-from-cpee"}}

                   {:description "custom-validation spv anomaly"
                    :steps [{:f stateful-device/acs-set-parameter-values!
                             :args [{"StatefulDeviceAtom-test-parameter" :does-not-matter!}]}]
                    :expected-match-last-step {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                                               ::test-success? :yep}}
                   {:description "get-parameter-names"
                    :supported-param-names #{"a.b.c"
                                             "a.b.c.d.e"
                                             "a.ziggy.c.d.e"
                                             "a.b.crazy"}
                    :steps [{:f stateful-device/acs-set-parameter-values!
                             :args [{"a.b.c.d.e-testing" 7}]}
                            {:f stateful-device/get-parameter-names
                             :args ["a.*.c."]}]
                    :expected-match-last-step #{"a.b.c.d.e-testing"
                                                "a.b.c.d.e"
                                                "a.ziggy.c.d.e"}}
                   {:description "add-object"
                    :steps [{:f stateful-device/acs-add-object!
                             :args ["fancy-object." "fancy object was here!"]}]
                    :expected-match-last-step {:instance-number 0}
                    :expected-match-state {:object-instances {"fancy-object." {:current-index-max 0
                                                                               :source :acs}}
                                           :supported-param-names #(contains? % "fancy-object.")}}

                   {:description "add-object with name now supported"
                    :steps [{:f stateful-device/acs-add-object!
                             :args ["fancy-object." "fancy object was here!"]}
                            {:f stateful-device/get-parameter-names
                             :args ["fancy-object."]}]
                    :expected-match-last-step #{"fancy-object."}}

                   {:description "update processor state"
                    :steps [{:f stateful-device/update-processor-state!
                             :args [(constantly "this is the state")]}
                            {:f stateful-device/get-processor-state}]
                    :expected-match-last-step "this is the state"}]]
    (doseq [{:keys [description
                    initial-cpe-parameter-values
                    steps
                    supported-param-names
                    expected-match-state
                    expected-match-device
                    expected-match-last-step]} use-cases]
      (testing description
        (let [device (stateful-device-atom/stateful-device-atom
                      "fefefe012345"
                      (or initial-cpe-parameter-values {})
                      {:supported-param-names supported-param-names
                       :acs-url "fake-acs-url"})
              last-step-result (last (for [{:keys [f args]} steps]
                                       (apply f device args)))]
          (when expected-match-device
            (is (match? expected-match-device device)))
          (when expected-match-state
            (is (match? expected-match-state
                        @(:state device))))
          (when expected-match-last-step
            (is (match? expected-match-last-step last-step-result))))))))
