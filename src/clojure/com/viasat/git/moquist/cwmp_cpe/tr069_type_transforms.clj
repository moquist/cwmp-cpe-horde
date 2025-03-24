(ns com.viasat.git.moquist.cwmp-cpe.tr069-type-transforms
  (:require [clojure.string :as str]
            [com.viasat.git.moquist.cwmp-cpe.util.time :as time-util]
            [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(def xsi-types
  "The following is just a starting point that may be enough to get a CPE online with a given ACS.
  This map may be custom-extended by specifying
  {:tr069 {:additional-tr181-name->xsd-type {}}} in the provided config file.
  Any configured extensions are merged with 'merge.
  Indexed names should take the form .{i}. for each index.
  E.g., at runtime, the following name:
  Device.X_COMPANY-COM_MAGIC.Interface.3.Mojo.7.Remote.0.SocketJuice
  ... will match the following entry in this map:
  Device.X_COMPANY-COM_MAGIC.Interface.{i}.Mojo.{i}.Remote.{i}.SocketJuice
  "
  (atom {"Manufacturer" "xsd:string"
         "OUI" "xsd:string"
         "ProductClass" "xsd:string"
         "SerialNumber" "xsd:string"
         "Device.DeviceInfo.ProvisioningCode" "xsd:string"
         "Device.ManagementServer.ParameterKey" "xsd:string"
         "Device.ManagementServer.ConnectionRequestURL" "xsd:string"
         "Device.DeviceInfo.SpecVersion" "xsd:string"
         "Device.DeviceInfo.Manufacturer" "xsd:string"
         "Device.DeviceInfo.SerialNumber" "xsd:string"
         "Device.DeviceInfo.HardwareVersion" "xsd:string"
         "Device.DeviceInfo.SoftwareVersion" "xsd:string"
         "Device.DeviceInfo.ProductClass" "xsd:string"
         "Device.DeviceInfo.ManufacturerOUI" "xsd:string"
         "Device.ManagementServer.PeriodicInformTime" "xsd:string"}))

(defn infer-xsi-type-from-value
  [k v]
  (when-let [xsd-type (cond
                        (string? v) "xsd:string"
                        (integer? v) "xsd:int"
                        (boolean? v) "xsd:boolean"
                        :else nil)]

    (log/warn {:message "inferred unspecified type by value"
               :parameter-name k
               :parameter-value v
               :inferred-type xsd-type})
    xsd-type))

(defn indexify-tr181-name
  "TR181 names may be indexed, like: :abc.1.def.7.efg
  Transform each .[0-9]+. sequence into .{i}."
  [nom]
  (str/replace nom #"\.[0-9]+\." ".{i}."))

(defn get-xsi-type [k v]
  (or (get @xsi-types (indexify-tr181-name (name k)))
      (infer-xsi-type-from-value k v)
      ;; XXX Ewww. This is a testing tool. Best to fail loudly until we know more about the behavior we want.
      (throw (Exception. (format "Don't know type for k %s and v %s" k v)))))

(def xsi-transform-to {"xsd:string" str
                       "xsd:boolean" #(if % "1" "0")
                       "xsd:dateTime" #(time-util/format-datetime (time-util/seconds->datetime %))})

(def soap-dateTime-format "yyyy-MM-dd'T'HH:mm:ssXXX")
(def xsi-transform-from {"xsd:int" #(Integer/parseInt %)
                         "xsd:unsignedInt" #(Long/parseLong %)
                         "xsd:boolean" #(boolean (or (= "1" %) (= "true" %)))
                         "xsd:dateTime" #(time-util/parse-datetime % soap-dateTime-format)})

(defn to-xsi
  "Given a kv, return a map containing the k, a transformed value, and the xsi data type of v.
   Data type defaults to string if type does not have a specific transformer fn"
  [[k v]]
  (let [xsi-type (get-xsi-type k v)
        transform-f (get xsi-transform-to xsi-type identity)]
    {:cwmp-name k
     :value (transform-f v)
     :xsi-type xsi-type}))
