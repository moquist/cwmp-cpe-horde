(ns com.viasat.git.moquist.cwmp-client.stateful-device.specs
  (:require [clojure.spec.alpha :as s]))

(s/def :com.viasat.git.moquist.cwmp-client/tr069-parameter-name string?)
(s/def :com.viasat.git.moquist.cwmp-client/tr069-parameter-value any?)
(s/def :com.viasat.git.moquist.cwmp-client/tr069-parameter-value-source
  #{:cpe :acs})

(s/def :com.viasat.git.moquist.cwmp-client.stateful-device/spvs
  (s/map-of :com.viasat.git.moquist.cwmp-client/tr069-parameter-name
            :com.viasat.git.moquist.cwmp-client/tr069-parameter-value))
(s/def :com.viasat.git.moquist.cwmp-client.stateful-device/spv-sources
  (s/map-of :com.viasat.git.moquist.cwmp-client/tr069-parameter-name
            :com.viasat.git.moquist.cwmp-client/tr069-parameter-value-source))
