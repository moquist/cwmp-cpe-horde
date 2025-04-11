(ns viasat.ring.middleware.http-digest.nonce-test
  (:require [clojure.test :refer [deftest is]]
            [viasat.ring.middleware.http-digest.nonce :as nonce]))

(set! *warn-on-reflection* true)

(deftest nonce-valid?-test
  (let [nonce (nonce/generate-nonce)]
    (nonce/add-nonce! nonce)
    (is (nonce/nonce-valid? nonce)))
  (binding [nonce/*nonce-timeout-millis* 0]
    (let [nonce (nonce/generate-nonce)]
      (nonce/add-nonce! nonce)
      (Thread/sleep 1)
      (is (not (nonce/nonce-valid? nonce))))))
