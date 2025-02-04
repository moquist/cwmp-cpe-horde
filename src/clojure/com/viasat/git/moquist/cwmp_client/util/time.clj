(ns com.viasat.git.moquist.cwmp-client.util.time
  (:import
   (java.text SimpleDateFormat)
   (java.util Date TimeZone)))

(set! *warn-on-reflection* true)

(def iso-8601 "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

(defn- datetime-formatter ^SimpleDateFormat [& [format]]
  (doto (SimpleDateFormat. (or format iso-8601))
    (.setTimeZone (TimeZone/getTimeZone "UTC"))))

(defn format-datetime
  "Convert a java.util.Date to a string, formatted according to ISO 8601"
  [date & [format]]
  (assert (instance? java.util.Date date))
  (.format (datetime-formatter format) ^Date date))

(defn parse-datetime
  "Parse a string formatted according to ISO 8601 into a java.util.Date.

  Throws clojure.lang.ExceptionInfo if s cannot be parsed."
  [s & [format]]
  (try
    (.parse (datetime-formatter format) s)
    (catch java.text.ParseException _ex
      (throw (ex-info (str "Invalid timestamp: " s) {:invalid-timestamp s})))))

(defn millis->datetime
  "Look it up once, write it down."
  [millis]
  (-> (long millis) java.util.Date. format-datetime))

(defn seconds->datetime
  "Convert a UNIX timestamp in seconds into a datetime in ISO 8601 format."
  [seconds]
  (millis->datetime (* seconds 1000)))

(def now
  "UTC"
  #(java.util.Date.))

(defn datetime->millis
  [^java.util.Date date]
  (.getTime date))
