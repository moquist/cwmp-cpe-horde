(ns viasat.cwmp-cpe.basic-cpe
  (:require [clj-http.client :as http]
            [clj-http.cookies]
            [clojure.data.xml :as xml]
            [clojure.xml :as c-xml]
            [taoensso.timbre :as log]
            [viasat.cwmp-cpe.handlers :as handlers]
            [viasat.cwmp-cpe.messages.inform-events :as informs]
            [viasat.cwmp-cpe.stateful-device :as stateful-device]
            [viasat.cwmp-cpe.util.time :as time-util]
            [viasat.cwmp-cpe.util.tr069.summarize :as summarize-util]))

(set! *warn-on-reflection* true)

(defn str->inputstream
  "https://stackoverflow.com/a/38284236"
  ([^java.lang.String s] (str->inputstream s "UTF-8"))
  ([^java.lang.String s ^java.lang.String encoding]
   (java.io.ByteArrayInputStream. (.getBytes s encoding))))

(defn send-request [device-id url {:as request :keys [body]}]
  (let [request (cond-> (merge {:headers {"content-type" "application/xml"}} request)
                  (seq body) (update :body (comp xml/indent-str xml/sexp-as-element)))]
    (log/trace device-id {::send-request (summarize-util/summarize-tr069-message body)})
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
    (log/debug :inform-session!-sending
               (stateful-device/get-serial-number stateful-device)
               (summarize-util/summarize-tr069-message inform-body))
    (loop [acs-message (send-request SerialNumber url (assoc request-base :body inform-body))
           session-end-pending? false]
      (log/debug :inform-session!-received
                 (stateful-device/get-serial-number stateful-device)
                 (summarize-util/summarize-tr069-message acs-message))
      (let [{:keys [message session-end-offer?]} (handlers/handle-acs-message stateful-device acs-message)]
        (log/debug :inform-session!-next
                   (stateful-device/get-serial-number stateful-device)
                   (summarize-util/summarize-tr069-message message)
                   :session-end-offer? session-end-offer?)
        (cond
          message (recur (send-request SerialNumber url (assoc request-base :body message))
                         ;; once the CPE has sent an empty request, hold that state until the end of the session
                         ;; 3.7.2.4 Session Termination
                         session-end-pending?)
          session-end-pending? :inform-session!-done
          :else (recur (send-request SerialNumber url request-base)
                       ;; initiate session termination because "the CPE has no further requests to send the ACS"
                       ;; 3.7.2.4 Session Termination
                       true))))))

(defn periodic-inform-now? [stateful-device]
  (let [{:keys [latest-inform]} (stateful-device/get-processor-state stateful-device)
        device-id (:SerialNumber (stateful-device/get-device-id stateful-device))
        interval-seconds (-> stateful-device
                             (stateful-device/get-parameter-values ["Device.ManagementServer.PeriodicInformInterval"])
                             vals
                             first)
        millis-since-latest-inform (when (instance? java.util.Date latest-inform)
                                     (- (time-util/datetime->millis (time-util/now))
                                        (time-util/datetime->millis latest-inform)))]
    (if-not (number? interval-seconds)
      (log/trace device-id {::periodic-inform-now? "periodic inform disabled because Device.ManagementServer.PeriodicInformInterval is not a number"})
      ;; DO NOT multiply interval-seconds by 1000, in order to inform 1000 times as often. :D
      (or (not millis-since-latest-inform)
          (< interval-seconds millis-since-latest-inform)))))

(defn cwmp-cpe-fn [{:as stateful-device :keys [acs-url]} & [{:keys [inform-session!-fn sleep-duration-fn]}]]
  (let [inform-session!-fn (or inform-session!-fn inform-session!)
        sleep-duration-fn (or sleep-duration-fn
                              #(+ 10000 (rand-int 5000)))
        {:keys [events value-change-parameter-names]}
        (stateful-device/get-processor-state stateful-device)]
    (cond
      (stateful-device/cnr-now? stateful-device)
      (do
        (inform-session!-fn stateful-device acs-url (informs/compose-connection-request stateful-device))
        (stateful-device/cnr-reset! stateful-device)
        (let [now (time-util/now)]
          (stateful-device/update-processor-state! stateful-device
                                                   #(-> %
                                                        (update :events conj {:event-type :connection-request :event-time now})
                                                        (assoc :latest-inform now)))))

      (seq value-change-parameter-names)
      (do
        (inform-session!-fn stateful-device acs-url (informs/compose-value-change stateful-device value-change-parameter-names))
        (let [now (time-util/now)]
          (stateful-device/update-processor-state! stateful-device
                                                   #(-> %
                                                        (dissoc :value-change-parameter-names)
                                                        (update :events conj {:event-type :value-change :event-time now})
                                                        (assoc :latest-inform now)))))

      ;; BOOTSTRAP has already been done
      (and (seq events)
           (->> events
                (map :event-type)
                set
                :bootstrap))
      (when (periodic-inform-now? stateful-device)
        (inform-session!-fn stateful-device acs-url (informs/compose-periodic-inform stateful-device))
        (let [now (time-util/now)]
          (stateful-device/update-processor-state! stateful-device
                                                   #(-> %
                                                        (update :events conj {:event-type :periodic-inform :event-time now})
                                                        (assoc :latest-inform now)))))

      :else
      (do
        (inform-session!-fn stateful-device acs-url (informs/compose-bootstrap-boot stateful-device))
        (let [now (time-util/now)]
          (stateful-device/update-processor-state! stateful-device
                                                   (constantly
                                                    {:events [{:event-type :bootstrap :event-time now}
                                                              {:event-type :boot :event-time now}]
                                                     :latest-inform now})))))

    (Thread/sleep ^java.lang.Long (sleep-duration-fn))))


