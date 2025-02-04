(ns com.viasat.git.moquist.cwmp-client.anomalies)

(defn anomaly? [x]
  (when (and (map? x) (:cognitect.anomalies/category x)) x))
