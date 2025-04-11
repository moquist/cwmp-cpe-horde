(ns viasat.cwmp-cpe.messages.inform-events
  (:require [viasat.cwmp-cpe.messages :as messages]
            [viasat.cwmp-cpe.stateful-device :as stateful-device]))

(defn compose-bootstrap-boot [stateful-device]
  (let [{:as DeviceId :keys [SerialNumber]} (stateful-device/get-device-id stateful-device)
        ParameterList (stateful-device/get-parameter-values stateful-device [])
        Event ["0 BOOTSTRAP" "1 BOOT"]]
    (->> (messages/tr069-inform
          SerialNumber
          {:DeviceId DeviceId
           :ParameterList ParameterList
           :Event Event}))))

(defn compose-periodic-inform [stateful-device]
  (let [{:as DeviceId :keys [SerialNumber]} (stateful-device/get-device-id stateful-device)
        ProvisioningCode (stateful-device/get-parameter-values stateful-device ["Device.DeviceInfo.ProvisioningCode"])
        ParameterList (stateful-device/get-parameter-values stateful-device [])
        Event ["2 PERIODIC"]]
    (->> (messages/tr069-inform
          SerialNumber
          {:DeviceId DeviceId
           :ParameterList (merge ParameterList ProvisioningCode)
           :Event Event}))))

(defn compose-connection-request [stateful-device]
  (let [{:as DeviceId :keys [SerialNumber]} (stateful-device/get-device-id stateful-device)
        ProvisioningCode (stateful-device/get-parameter-values stateful-device ["Device.DeviceInfo.ProvisioningCode"])
        ParameterList (stateful-device/get-parameter-values stateful-device [])
        Event ["6 CONNECTION REQUEST"]]
    (->> (messages/tr069-inform
          SerialNumber
          {:DeviceId DeviceId
           :ParameterList (merge ParameterList ProvisioningCode)
           :Event Event}))))

(defn compose-boot [stateful-device]
  (let [{:as DeviceId :keys [SerialNumber]} (stateful-device/get-device-id stateful-device)
        ParameterList (stateful-device/get-parameter-values stateful-device [])
        Event ["1 BOOT"]]
    (->> (messages/tr069-inform
          SerialNumber
          {:DeviceId DeviceId
           :ParameterList ParameterList
           :Event Event}))))

(defn compose-value-change [stateful-device parameter-names]
  (let [{:as DeviceId :keys [SerialNumber]} (stateful-device/get-device-id stateful-device)
        ParameterList (stateful-device/get-parameter-values stateful-device parameter-names)
        Event ["4 VALUE CHANGE"]]
    (->> (messages/tr069-inform
          SerialNumber
          {:DeviceId DeviceId
           :ParameterList ParameterList
           :Event Event}))))


