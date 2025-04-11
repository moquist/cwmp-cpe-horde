(ns viasat.cwmp-cpe.util.xml-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test]
            [viasat.cwmp-cpe.util.xml :as xml-util]))

(deftest xml-tag->lowercased-namespace-test
  (is (= :cwmp:Meep (xml-util/xml-tag->lowercased-namespace :CWMP:Meep)))
  (is (= :cwmp:Meep (xml-util/xml-tag->lowercased-namespace :cwmp:Meep)))
  (is (= :Meep (xml-util/xml-tag->lowercased-namespace :Meep))))

(deftest xml-tags=-test
  (let [test-cases [{:description "equal"
                     :tags [:some-ns:whatevs :SOME-NS:whatevs]
                     :expected true}
                    {:description "equal without xml namespaces"
                     :tags [:whatevs :whatevs]
                     :expected true}
                    {:description "case-sensitivity for tags"
                     :tags [:whatevs :whatEVS]
                     :expected false}
                    {:description "not equal: tags"
                     :tags [:some-ns:gleebo :SOME-NS:whatevs]
                     :expected false}
                    {:description "not equal: namespaces"
                     :tags [:some-ns:zeep :SOME-OTHER-NS:zeep]
                     :expected false}
                    {:description "does not blow up with non-keyword inputs"
                     :tags [1 nil :meep]
                     :expected false}]]
    (doseq [{:keys [description tags expected]} test-cases]
      (testing description
        (is (= expected
               (apply xml-util/xml-tags= tags)))))))

(deftest xml->map-xml-test
  (is (= {:tag :SOAP-ENV:Envelope,
          :attrs {:xmlns:cwmp "urn:dslforum-org:cwmp-1-2",
                  :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance",
                  :xmlns:xsd "http://www.w3.org/2001/XMLSchema",
                  :xmlns:SOAP-ENC "http://schemas.xmlsoap.org/soap/encoding/",
                  :xmlns:SOAP-ENV "http://schemas.xmlsoap.org/soap/envelope/"},
          :content [{:tag :SOAP-ENV:Header,
                     :attrs nil,
                     :content [{:tag :cwmp:ID,
                                :attrs {:SOAP-ENV:mustUnderstand "1"},
                                :content ["FR-device-in-repl-1736280146837"]}]}
                    {:tag :SOAP-ENV:Body, :attrs nil, :content [{:tag :cwmp:FactoryReset, :attrs nil, :content nil}]}]}
         (xml-util/xml->map-xml
          [:SOAP-ENV:Envelope
           {:xmlns:SOAP-ENV "http://schemas.xmlsoap.org/soap/envelope/",
            :xmlns:SOAP-ENC "http://schemas.xmlsoap.org/soap/encoding/",
            :xmlns:xsd "http://www.w3.org/2001/XMLSchema",
            :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance", :xmlns:cwmp "urn:dslforum-org:cwmp-1-2"}
           [:SOAP-ENV:Header
            nil
            [:cwmp:ID {:SOAP-ENV:mustUnderstand "1"} "FR-device-in-repl-1736280146837"]]
           [:SOAP-ENV:Body nil [:cwmp:FactoryReset]]]))))


