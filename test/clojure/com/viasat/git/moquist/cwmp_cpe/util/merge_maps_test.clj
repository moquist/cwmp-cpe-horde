(ns com.viasat.git.moquist.cwmp-cpe.util.merge-maps-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.viasat.git.moquist.cwmp-cpe.util.merge-maps :refer [merge-maps]]
            [matcher-combinators.test]))

(deftest merge-maps-test
  (let [test-cases [{:description "basic"
                     :args [{:a 1 :b {:b.a 3}}
                            {:c 3 :b {:b.b 4}}]
                     :expected-match {:a 1 :b {:b.a 3 :b.b 4} :c 3}}
                    {:description "nils"
                     :args [{:a 1 :b {:b.a 3}}
                            {:c 3 :b {:b.b nil}}
                            {:c 3 :b {:b.b 4}}
                            nil]
                     :expected-match {:a 1 :b {:b.a 3 :b.b 4} :c 3}}
                    {:description "nuthin"
                     :args [nil]
                     :expected-match nil?}]]
    (doseq [{:keys [description args expected-match]} test-cases]
      (testing description
        (is (match? expected-match
                    (apply merge-maps args)))))))
