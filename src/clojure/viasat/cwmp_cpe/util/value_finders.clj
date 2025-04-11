(ns viasat.cwmp-cpe.util.value-finders)

(set! *warn-on-reflection* true)

;; XXX refactor to take multiple preds for structure-based fuzzy drill-down?
(defn find-value
  "Thx, glean"
  [pred data]
  (cond
    (pred data) data
    (map? data) (some (partial find-value pred) (vals data))
    (coll? data) (some (partial find-value pred) data)
    :else nil))

(defn collect-values
  "like find-value, but return all the matches instead of just the first one.
  Cannot be used to collect nil."
  ([pred data]
   (cond
     (pred data) data
     (coll? data) (flatten
                   (remove
                    nil?
                    (for [val (if (map? data) (vals data) data)]
                      (collect-values pred val)))))))
