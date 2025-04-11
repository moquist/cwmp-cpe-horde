(ns viasat.ring.middleware.http-digest-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test]
            [viasat.ring.middleware.http-digest :as http-digest]
            [viasat.ring.middleware.http-digest.nonce :as nonce]))

(set! *warn-on-reflection* true)

(deftest parse-authorization-header-test
  (is (= (http-digest/parse-authorization-header
          "Digest username=\"user\", realm=\"my-realm\", nonce=\"nonce2\", uri=\"/dir/index.html\", cnonce=\"M=GYxMGQzMWY4MDIyYzQxZWY1ODNjYzAzMDJjOTYyNzI=\", nc=00000001, qop=auth, response=\"84261b2ae787ada9c6508d7f9c30a308\", opaque=\"\"")
         {"username" "user",
          "realm" "my-realm",
          "nonce" "nonce2",
          "uri" "/dir/index.html",
          "cnonce" "M=GYxMGQzMWY4MDIyYzQxZWY1ODNjYzAzMDJjOTYyNzI=",
          "response" "84261b2ae787ada9c6508d7f9c30a308",
          "qop" "auth",
          "nc" "00000001"})))

(deftest authenticate-test
  (nonce/add-nonce! "d096c24bfa0710f763733a816e150fc3")
  (is (http-digest/authenticate
       {"xLbddQogrHRi" "t5z6L2hNCDBl"}
       {:request-method :get
        :uri "/cpes/FEFEFE000004"
        :headers {"authorization"
                  "Digest username=\"xLbddQogrHRi\", realm=\"TR-069 Connection Request (cwmp-cpe-horde)\", nonce=\"d096c24bfa0710f763733a816e150fc3\", uri=\"/cpes/FEFEFE000004\", cnonce=\"MTk4ZTljNjRhNDg2NTcxZDgxNmIzMTQ1ZDBhODkyMGU=\", nc=00000001, qop=auth, response=\"1291e986e51108168280c0f8d8ccd778\", opaque=\"\""}})))

(defn- parse-www-authenticate-header [header]
  (let [quoted (->> (re-seq #"([a-z]+)=\"([^\"]+)\"" header)
                    (mapv rest)
                    (mapv vec)
                    (into {}))
        [_ qop] (re-find #"qop=\"?([a-z]+)\"?," header)]
    (assoc quoted "qop" qop)))

(deftest wrap-digest-auth-test
  (let [username "testuser"
        password "testpassword"
        username->password {username password}
        uri "/cpes/0123456789AB"
        test-realm "test-realm"
        cnonce "test-cnonce"
        success-response "You have found the secret message!"

        wrapper-fn (http-digest/wrap-digest-auth
                    (constantly success-response)
                    username->password
                    {:realm test-realm})

        {:as unauthenticated-result
         :keys [headers]}
        (wrapper-fn {:request-method :get :uri uri})

        {:strs [realm nonce qop opaque]}
        (parse-www-authenticate-header (get headers "WWW-Authenticate"))

        response-digest (http-digest/generate-response-digest
                         (http-digest/generate-ha1 username realm password)
                         (http-digest/generate-ha2 "GET" uri)
                         nonce
                         "00000001"
                         cnonce
                         qop)

        authenticated-result
        (wrapper-fn {:request-method :get
                     :uri uri
                     :headers {"authorization"
                               (format "Digest username=\"%s\", realm=\"%s\", nonce=\"%s\", uri=\"%s\", cnonce=\"%s\", nc=00000001, qop=auth, response=\"%s\", opaque=\"%s\""
                                       username
                                       realm
                                       nonce
                                       uri
                                       cnonce
                                       response-digest
                                       opaque)}})]
    (testing "first request is unauthorized and response includes auth details"
      (is (match?
           {:body "Unauthorized",
            :status 401,
            :headers {"WWW-Authenticate"
                      (format "Digest realm=\"%s\", qop=\"auth\", nonce=\"%s\", opaque=\"\""
                              realm
                              nonce)}}
           unauthenticated-result)))
    (testing "second request is authorized"
      (is (= success-response authenticated-result)))))
