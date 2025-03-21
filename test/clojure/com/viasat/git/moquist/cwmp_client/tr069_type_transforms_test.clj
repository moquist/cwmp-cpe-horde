(ns com.viasat.git.moquist.cwmp-client.tr069-type-transforms-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.viasat.git.moquist.cwmp-client.tr069-type-transforms :as tr069-type-transforms]))

(deftest indexify-tr181-name-test
  (is (= (tr069-type-transforms/indexify-tr181-name "Device.X_COMPANY-COM_MAGIC.Interface.300.Mojo.7.Remote.0.SocketJuice")
         "Device.X_COMPANY-COM_MAGIC.Interface.{i}.Mojo.{i}.Remote.{i}.SocketJuice")))

(deftest infer-xsi-type-from-value-test
  (let [use-cases [{:description "int", :input 7, :expected "xsd:int"}
                   {:description "string", :input "some string", :expected "xsd:string"}
                   {:description "boolean", :input false, :expected "xsd:boolean"}
                   {:description "unknown", :input :keyword-wat, :expected nil}]]
    (doseq [{:keys [description input expected]} use-cases]
      (testing description
        (is (= (tr069-type-transforms/infer-xsi-type-from-value "only-for-logging" input)
               expected))))))
