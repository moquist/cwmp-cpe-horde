(ns com.viasat.git.moquist.cwmp-client.validate-tr069-parameters
  "Allow extensible and customizable validation of TR-069 parameters for various test-scenario purposes.
  A handful of TR-181 validators are implemented, merely as demonstrations."
  (:require [clojure.string :as str]
            [com.viasat.git.moquist.cwmp-client.anomalies :refer [anomaly?]]))

(defn -strlen-fn [k v min-length-inclusive max-length-inclusive]
  (or (and (string? v)
           (<= min-length-inclusive (count v))
           (<= (count v) max-length-inclusive))
      {:cognitect.anomalies/category :cognitect.anomalies/incorrect
       :parameter-name k
       :parameter-value v
       :message (format "Parameter %s invalid: value must be string <= length %s, but found %s"
                        k max-length-inclusive v)}))

(defmulti validate-parameter
  "Dispatch on parameter-name as a string.
  If parameter is valid, return anything truthy except an anomaly.
  If parameter is invalid, return an anomaly."
  (fn [[k _v]]
    ;; Allow keyword keys, but dispatch on strings only.
    (str/replace (name k) #"\.[0-9]+\." ".{i}.")))

(defmethod validate-parameter :default
  ;; validation is opt-in
  [[_k _v]]
  (constantly true))

(defmethod validate-parameter "Device.DeviceInfo.Manufacturer"
  [[k v]]
  ;; TR-181
  (-strlen-fn k v 0 64))

(defmethod validate-parameter "Device.DeviceInfo.ManufacturerOUI"
  [[k v]]
  ;; TR-181
  (-strlen-fn k v 6 6))

(defmethod validate-parameter "Device.DeviceInfo.SerialNumber"
  [[k v]]
  ;; TR-181
  (-strlen-fn k v 0 64))

(defmethod validate-parameter "Device.DeviceInfo.HardwareVersion"
  [[k v]]
  ;; TR-181
  (-strlen-fn k v 0 64))

(defmethod validate-parameter "Device.DeviceInfo.SoftwareVersion"
  [[k v]]
  ;; TR-181
  (-strlen-fn k v 0 64))

(defmethod validate-parameter "Device.DeviceInfo.ProvisioningCode"
  [[k v]]
  ;; TR-181
  (-strlen-fn k v 0 64))

(defn validate-parameters
  "This is intended to validate TR-069 parameters via multimethod dispatch on the parameter name.
  But really, it operates on any associative data that Clojure treats like a map.
  Returns true when all kvs are valid, or the first-found anomaly otherwise."
  [kvs]
  (or (some anomaly? (map validate-parameter kvs))
      true))
