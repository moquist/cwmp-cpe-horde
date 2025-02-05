(ns com.viasat.git.moquist.cwmp-client.tr069-type-transforms
  (:require [com.viasat.git.moquist.cwmp-client.util.time :as time-util]))

(set! *warn-on-reflection* true)

(def xsi-types
  "Currently populated from a few samples; should be populated from comprehensive source"
  {:Manufacturer "xsd:string"
   :OUI "xsd:string"
   :ProductClass "xsd:string"
   :SerialNumber "xsd:string"
   :Device.DeviceInfo.ProvisioningCode "xsd:string"
   :Device.ManagementServer.ParameterKey "xsd:string"
   :Device.ManagementServer.ConnectionRequestURL "xsd:string"
   :Device.DeviceInfo.SpecVersion "xsd:string"
   :Device.DeviceInfo.Manufacturer "xsd:string"
   :Device.DeviceInfo.SerialNumber "xsd:string"
   :Device.DeviceInfo.HardwareVersion "xsd:string"
   :Device.DeviceInfo.SoftwareVersion "xsd:string"
   :Device.DeviceInfo.ProductClass "xsd:string"
   :Device.DeviceInfo.ManufacturerOUI "xsd:string"})

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
  (let [xsi-type (get xsi-types k "xsd:string")
        transform-f (get xsi-transform-to xsi-type str)]
    {:cwmp-name k
     :value (transform-f v)
     :xsi-type xsi-type}))
