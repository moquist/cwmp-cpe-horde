(ns viasat.cwmp-cpe.util.tr069-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test]
            [viasat.cwmp-cpe.util.tr069 :as tr069-util]))

(deftest tr069-wildcard-path-match-test
  (let [use-cases [{:description "literal matching"
                    :patterns ["a.b" "c.d"]
                    :available-parameters ["a.b" "c.d" "e.f"]
                    :expected-match #{"a.b" "c.d"}}
                   {:description "wildcard matching"
                    :patterns ["a.*.c"]
                    :available-parameters ["a.b.c" "a.c.d" "a..c" "a.c" "ac" "a.booga.wooga.c"]
                    :expected-match #{"a.b.c" "a..c" "a.booga.wooga.c"}}
                   {:description "final dot is the same as dot followed by anything"
                    :patterns ["a.b."]
                    :available-parameters ["a.b.c" "a.b.c.d" "a.b" "a"]
                    :expected-match #{"a.b.c" "a.b.c.d"}}
                   {:description "empty matches everything"
                    :patterns []
                    :available-parameters ["a.b.c" "a"]
                    :expected-match #{"a.b.c" "a"}}]]
    (doseq [{:keys [description patterns available-parameters expected-match]} use-cases]
      (testing description
        (is (match? expected-match
                    (set (tr069-util/tr069-wildcard-path-match patterns available-parameters))))))))


