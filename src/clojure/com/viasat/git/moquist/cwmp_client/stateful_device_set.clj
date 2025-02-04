(ns com.viasat.git.moquist.cwmp-client.stateful-device-set)

(defprotocol StatefulDeviceSet
  (-get-device [this device-id])
  (-list-devices [this]))
