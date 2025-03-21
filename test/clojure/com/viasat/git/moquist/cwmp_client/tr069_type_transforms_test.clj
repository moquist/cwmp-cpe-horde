(ns com.viasat.git.moquist.cwmp-client.tr069-type-transforms-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.viasat.git.moquist.cwmp-client.tr069-type-transforms :as tr069-type-transforms]))

(deftest infer-xsi-type-from-value-test
  (let [use-cases [{:description "int", :input 7, :expected "xsd:int"}
                   {:description "string", :input "some string", :expected "xsd:string"}
                   {:description "boolean", :input false, :expected "xsd:boolean"}
                   {:description "unknown", :input :keyword-wat, :expected nil}]]
    (doseq [{:keys [description input expected]} use-cases]
      (testing description
        (is (= (tr069-type-transforms/infer-xsi-type-from-value "only-for-logging" input)
               expected))))))
