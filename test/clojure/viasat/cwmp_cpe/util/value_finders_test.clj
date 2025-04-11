(ns viasat.cwmp-cpe.util.value-finders-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [viasat.cwmp-cpe.util.value-finders :as value-finders]))

(deftest collect-values-test
  (let [f #(and (integer? %) (odd? %))
        test-cases [{:description "empty input"
                     :f f
                     :input []
                     :expected-match []}
                    {:description "nil input"
                     :f f
                     :input []
                     :expected-match []}
                    {:description "flat seq input"
                     :f f
                     :input (range 10)
                     :expected-match [1 3 5 7 9]}
                    {:description "nested seq input"
                     :f f
                     :input [0 1 2 [3 4] 5]
                     :expected-match [1 3 5]}
                    {:description "nested map and nested seq input"
                     :f f
                     :input [0 1 {:meep {:zorp 71}} 2 [3 4] 5]
                     ;; currently order-dependent; seems fine :shrug:
                     :expected-match [1 71 3 5]}]]
    (doseq [{:keys [description f input expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (value-finders/collect-values f input)))))))
