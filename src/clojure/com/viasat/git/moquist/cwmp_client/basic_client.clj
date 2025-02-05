(ns com.viasat.git.moquist.cwmp-client.basic-client
  (:require [clj-http.client :as http]
            [clj-http.cookies]
            [clojure.data.xml :as xml]
            [clojure.xml :as c-xml]
            [com.viasat.git.moquist.cwmp-client.handlers :as handlers]
            [com.viasat.git.moquist.cwmp-client.messages.inform-events :as informs]
            [com.viasat.git.moquist.cwmp-client.stateful-device :as stateful-device]
            [com.viasat.git.moquist.cwmp-client.util.time :as time-util]
            [com.viasat.git.moquist.cwmp-client.util.tr069.summarize :as summarize-util]
            [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn str->inputstream
  "https://stackoverflow.com/a/38284236"
  ([^java.lang.String s] (str->inputstream s "UTF-8"))
  ([^java.lang.String s ^java.lang.String encoding]
   (java.io.ByteArrayInputStream. (.getBytes s encoding))))

(defn send-request [_device-id url {:as request :keys [body]}]
  (let [request (cond-> (merge {:headers {"content-type" "application/xml"}} request)
                  (seq body) (update :body (comp xml/indent-str xml/sexp-as-element)))]
    (log/trace {::send-request (summarize-util/summarize-tr069-message body)})
    (-> (http/post url request)
        (select-keys [:status :body :cookies])
        (update :body #(when (seq %1) (c-xml/parse (str->inputstream %1)))))))

(defn inform-session! [stateful-device url {:keys [inform-body]} & [{:keys [send-request-fn]}]]
  ;; XXX rename to serial-number?
  (let [{:keys [SerialNumber]} (stateful-device/get-device-id stateful-device)
        ;; clj-http.cookies/cookie-store is an older version of org.apache.http.impl.client.BasicCookieStore that WARNS a lot like this:
        ;; WARNING: Invalid cookie header: "Set-Cookie: AWSALB=5j+Wfo/fge7NcYW/iNC7AN5lXjPwmXzXnUigbSnLBYSwKa+JOpQ8pv2TQP/gdtdhy20XC94+0bLXWd6n23LT7xLf6eVSp9JgMgJGkyPJAToEreQ7bMoP/NW+RVig; Expires=Tue, 11 Feb 2025 21:09:30 GMT; Path=/". Invalid 'expires' attribute: Tue, 11 Feb 2025 21:09:30 GMT
        request-base {:cookie-store (clj-http.cookies/cookie-store)}
        send-request (or send-request-fn send-request)]
    (log/debug :inform-session!-sending (summarize-util/summarize-tr069-message inform-body))
    (loop [acs-message (send-request SerialNumber url (assoc request-base :body inform-body))
           session-end-pending? false]
      (log/debug :inform-session!-received (summarize-util/summarize-tr069-message acs-message))
      (let [{:keys [message session-end-offer?]} (handlers/handle-acs-message stateful-device acs-message)]
        (log/debug :inform-session!-next (summarize-util/summarize-tr069-message message)
                   :session-end-offer? session-end-offer?)
        (cond
          message (recur (send-request SerialNumber url (assoc request-base :body message))
                         false)
          session-end-pending? :inform-session!-done
          :else (recur (send-request SerialNumber url request-base) true))))))

(defn periodic-inform-now? [stateful-device]
  (let [{:keys [latest-inform]} (stateful-device/get-processor-state stateful-device)
        interval-seconds (-> stateful-device
                             (stateful-device/get-parameter-values ["Device.ManagementServer.PeriodicInformInterval"])
                             vals
                             first)
        millis-since-latest-inform (when latest-inform
                                     (- (time-util/datetime->millis (time-util/now))
                                        (time-util/datetime->millis latest-inform)))]
    ;; DO NOT multiply interval-seconds by 1000, in order to inform 1000 times as often. :D
    (< interval-seconds millis-since-latest-inform)))

(defn cwmp-client-fn [{:as stateful-device :keys [acs-url]}]
  (let [{:keys [events value-change-parameter-names]}
        (stateful-device/get-processor-state stateful-device)]
    (cond
      (seq value-change-parameter-names)
      (do
        (inform-session! stateful-device acs-url (informs/compose-value-change stateful-device value-change-parameter-names))
        (stateful-device/update-processor-state! stateful-device
                                                 #(-> %
                                                      (dissoc :value-change-parameter-names)
                                                      (update :events conj :value-change)
                                                      (assoc :latest-inform (time-util/now)))))

      (and (seq events)
           ((set events) :bootstrap))
      (when (periodic-inform-now? stateful-device)
        (inform-session! stateful-device acs-url (informs/compose-periodic-inform stateful-device))
        (stateful-device/update-processor-state! stateful-device
                                                 #(-> %
                                                      (update :events conj :periodic-inform)
                                                      (assoc :latest-inform (time-util/now)))))

      :else
      (do
        (inform-session! stateful-device acs-url (informs/compose-bootstrap-boot stateful-device))
        (stateful-device/update-processor-state! stateful-device
                                                 (constantly
                                                  {:events [:bootstrap :boot]
                                                   :latest-inform (time-util/now)}))))

    (Thread/sleep ^java.lang.Long (+ 10000 (rand-int 5000)))))


