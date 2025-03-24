(ns com.viasat.git.moquist.cwmp-cpe.util.tr069
  (:require [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn generate-msg-id
  [prefix device-id]
  (str prefix "-" device-id "-" ^Long (System/currentTimeMillis)))

(defn wildcard-to-regex
  [s]
  (-> s
      (str/replace "." "\\.")
      (str/replace "*" ".*")))

(defn tr069-wildcard-path-match
  "A.2.4 Instance Wildcards
   A.3.2.2 GetParameterValues Table 18"
  [wildcard-paths available-parameter-names]
  ;; Considered and rejected clojure.spec.alpha. This could be more specific, but I believe YAGNI.
  {:pre [(every? string? wildcard-paths)
         (every? string? available-parameter-names)]}
  (if-not (seq wildcard-paths)
    ;; empty matches everything
    available-parameter-names
    (->> wildcard-paths
         (map (fn [path]
                (if (= \. (last path))
                  ;; period at the end is the equivalent of blah.*
                  (str path "*")
                  path)))
         (map (comp re-pattern wildcard-to-regex))
         (reduce
           ;; optimized a bit; don't keep checking for matches against parameters that have already been matched
          (fn [{:keys [unmatched-available matched-available]}
               regex-path]
            (let [new-matched-available (set (keep #(re-matches regex-path %) unmatched-available))]
              {:unmatched-available (remove new-matched-available unmatched-available)
               :matched-available (into matched-available new-matched-available)}))
          {:unmatched-available (set (map name available-parameter-names))
           :matched-available #{}})
         :matched-available)))

