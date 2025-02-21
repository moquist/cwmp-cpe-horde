(ns com.viasat.git.moquist.cwmp-client.stateful-device-set)

(defprotocol StatefulDeviceSet
  (-get-device [this device-id])
  (-list-devices [this]))

(defn get-device [stateful-device-set device-id]
  (assert (satisfies? StatefulDeviceSet stateful-device-set))
  (-get-device stateful-device-set device-id))

(defn list-devices [stateful-device-set]
  (assert (satisfies? StatefulDeviceSet stateful-device-set))
  (-list-devices stateful-device-set))
