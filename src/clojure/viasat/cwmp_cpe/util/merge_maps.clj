(ns viasat.cwmp-cpe.util.merge-maps)

(defn merge-maps
  "Recursively merge a sequence of maps.
  The last map for any given key path supplies a value.
  Nil values are ignored."
  [& args]
  (let [args (remove nil? args)]
    (if (every? map? args)
      (apply merge-with merge-maps args)
      (last args))))


