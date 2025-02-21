(ns com.viasat.git.moquist.ring.middleware.http-digest
  "Implements bare-bones HTTP Digest authentication (https://datatracker.ietf.org/doc/html/rfc2617) with support for qop=auth.

  auth-int and algorithms other than MD5 are unsupported."
  (:require
   [clojure.string :as str]
   [com.viasat.git.moquist.ring.middleware.http-digest.nonce :as nonce])
  (:import [org.apache.commons.codec.digest DigestUtils]))

(set! *warn-on-reflection* true)

(defn parse-authorization-header [header]
  (let [quoted (->> (re-seq #"([a-z]+)=\"([^\"]+)\"" header)
                    (mapv rest)
                    (mapv vec)
                    (into {}))
        [_ qop] (re-find #"qop=\"?([a-z]+)\"?," header)
        [_ nc] (re-find #"nc=([a-f0-9]{8})" header)]
    (assoc quoted
           "qop" qop
           "nc" nc)))

(defn generate-ha1 [username realm password]
  (DigestUtils/md5Hex (str username ":" realm ":" password)))

(defn generate-ha2 [method uri]
  (DigestUtils/md5Hex (str method ":" uri)))

(defn generate-response-digest [ha1 ha2 nonce nc cnonce qop]
  (DigestUtils/md5Hex (str ha1 ":" nonce ":" nc ":" cnonce ":" qop ":" ha2)))

(defn authenticate [username->password-fn {:keys [headers request-method uri] :as _request}]
  (let [auth-header (get headers "authorization")
        {:strs [username realm nonce nc cnonce qop response]} (parse-authorization-header auth-header)
        password (username->password-fn username)
        ha1 (generate-ha1 username realm password)
        ha2 (generate-ha2 (str/upper-case (name request-method))
                          uri)
        response-digest (generate-response-digest ha1 ha2 nonce nc cnonce qop)]
    (and (nonce/nonce-valid? nonce)
         (= response-digest response))))

(defn wrap-digest-auth [handler
                        username->password-fn
                        {:keys [realm opaque nonce-generator-fn]}]
  (let [realm (or realm "ring.middleware.http-digest")
        opaque (or opaque "")
        nonce-generator-fn (or nonce-generator-fn nonce/generate-nonce)]
    (fn [request]
      (if (get-in request [:headers "authorization"])
        ;; attempt to authenticate the request
        (if (authenticate username->password-fn request)
          (handler request)
          ;; authentication attempted, but failed
          {:body "Unauthorized" :status 401})
        ;; authentication not yet attempted, provide auth params
        (let [new-nonce (nonce/add-nonce! (nonce-generator-fn))]
          {:body "Unauthorized"
           :status 401
           :headers {"WWW-Authenticate"
                     (format "Digest realm=\"%s\", qop=\"auth\", nonce=\"%s\", opaque=\"%s\""
                             realm
                             new-nonce
                             opaque)}})))))
