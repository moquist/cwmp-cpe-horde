(ns com.viasat.git.moquist.ring.middleware.http-digest.nonce
  (:import [org.apache.commons.codec.digest DigestUtils]))

(set! *warn-on-reflection* true)

(def ^:dynamic *nonce-timeout-millis* 1000)

(defn generate-nonce []
  (DigestUtils/md5Hex (str (System/currentTimeMillis)
                           (rand-int 1000000))))

(def nonce->birthdate-millis
  (atom {}))

(defn nonce-gc! []
  (->> @nonce->birthdate-millis
       (filter (fn [[_nonce birthdate-millis]]
                 (< (System/currentTimeMillis)
                    (+ birthdate-millis *nonce-timeout-millis*))))
       (into {})))

(defn nonce-valid? [nonce]
  (nonce-gc!)
  (let [birthdate-millis (get @nonce->birthdate-millis nonce)]
    (and birthdate-millis
         (< (System/currentTimeMillis)
            (+ birthdate-millis *nonce-timeout-millis*)))))

(defn add-nonce! [nonce]
  (swap! nonce->birthdate-millis
         (fn [state]
           (assoc state nonce (System/currentTimeMillis))))
  nonce)


