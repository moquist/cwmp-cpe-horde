(ns com.viasat.git.moquist.cwmp-client.stateful-device
  (:require [clojure.spec.alpha :as s]
            [com.viasat.git.moquist.cwmp-client.stateful-device.specs]))

(defprotocol StatefulDevice
  (-get-device-id [this])
  ;; keep track of what got set by the ACS vs what was set locally or otherwise side-loaded, etc.
  (-cpe-set-parameter-values! [this kvs])
  (-acs-set-parameter-values! [this kvs])
  (-get-parameter-values [this names])
  (-get-parameter-names [this param-path])
  (-get-parameter-values-sources [this names])
  (-acs-add-object!
    [this object-name parameter-key]
    "In a single transaction, add the object and set the parameter [sic] key.
    Return an anomaly on failure, or a map with :instance-number and :status according to A.3.2.6 AddObject.")
  (-update-processor-state!
    [this f]
    "The cwmp-client-fn needs somewhere to keep state.
    E.g., this device is already bootstrapped, time since last boot, etc.
    Structure and format are owned by the caller, and opaque here.")
  (-get-processor-state [this])
  (-username->password
    [this username]
    "Verify that the specified username and password match what the ACS sent for:
    Device.ManagementServer.ConnectionRequestUsername
    Device.ManagementServer.ConnectionRequestPassword")
  (-notify-cnr!
    [this]
    "Notify this cwmp-client to initiate an Inform due to a Connection Request
    according to 3.2.2 ACS Connection Initiation")
  (-cnr-now?
    [this]
    "Inform now due to Connection Request")
  (-cnr-reset! [this] "Reset CNR"))

;; XXX clean up these assertions...

(defn get-device-id [stateful-device]
  (assert (satisfies? StatefulDevice stateful-device))
  (-get-device-id stateful-device))

(defn get-serial-number [stateful-device]
  (assert (satisfies? StatefulDevice stateful-device))
  (:SerialNumber (get-device-id stateful-device)))

(defn cpe-set-parameter-values! [stateful-device kvs]
  (assert (satisfies? StatefulDevice stateful-device))
  ;; TODO return anomalies for bad data from the ACS!
  (assert (s/valid? :com.viasat.git.moquist.cwmp-client.stateful-device/spvs kvs))
  (-cpe-set-parameter-values! stateful-device kvs))

(defn acs-set-parameter-values! [stateful-device kvs]
  (assert (satisfies? StatefulDevice stateful-device))
  ;; TODO return anomalies for bad data from the ACS!
  (assert (s/valid? :com.viasat.git.moquist.cwmp-client.stateful-device/spvs kvs))
  (-acs-set-parameter-values! stateful-device kvs))

(defn get-parameter-values [stateful-device names]
  (assert (satisfies? StatefulDevice stateful-device))
  ;; TODO return anomalies for bad data from the ACS!
  (assert (every? string? names))
  (-get-parameter-values stateful-device names))

(defn get-parameter-names [stateful-device param-path]
  (assert (satisfies? StatefulDevice stateful-device))
  ;; TODO return anomalies for bad data from the ACS!
  (assert (string? param-path))
  (-get-parameter-names stateful-device param-path))

(defn get-parameter-values-sources [stateful-device names]
  (assert (satisfies? StatefulDevice stateful-device))
  ;; TODO return anomalies for bad data from the ACS!
  (assert (every? string? names))
  (-get-parameter-values-sources stateful-device names))

(defn acs-add-object! [stateful-device object-name parameter-key]
  (assert (satisfies? StatefulDevice stateful-device))
  ;; TODO return anomalies for bad data from the ACS!
  (assert (string? object-name))
  (-acs-add-object! stateful-device object-name parameter-key))

(defn update-processor-state! [stateful-device f]
  (assert (satisfies? StatefulDevice stateful-device))
  (-update-processor-state! stateful-device f))

(defn get-processor-state [stateful-device]
  (assert (satisfies? StatefulDevice stateful-device))
  (-get-processor-state stateful-device))

(defn username->password [stateful-device username]
  (assert (satisfies? StatefulDevice stateful-device))
  (-username->password stateful-device username))

(defn notify-cnr! [stateful-device]
  (assert (satisfies? StatefulDevice stateful-device))
  (-notify-cnr! stateful-device))

(defn cnr-now? [stateful-device]
  (assert (satisfies? StatefulDevice stateful-device))
  (-cnr-now? stateful-device))

(defn cnr-reset! [stateful-device]
  (assert (satisfies? StatefulDevice stateful-device))
  (-cnr-reset! stateful-device))
