(ns viasat.cwmp-cpe.anomalies)

(defn anomaly? [x]
  (when (and (map? x) (:cognitect.anomalies/category x)) x))
