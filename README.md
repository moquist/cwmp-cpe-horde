# CWMP CPE Horde

[![Tests](https://github.com/Viasat/cwmp-cpe-horde/actions/workflows/test.yaml/badge.svg)](https://github.com/Viasat/cwmp-cpe-horde/actions/workflows/test.yaml)

TR-069 (AKA "CPE WAN Management Protocol", or CWMP) is a standardized protocol for managing edge devices over a WAN. See https://www.broadband-forum.org/pdfs/tr-069-1-6-1.pdf for the latest (_caveat lector_) specification document.

This CWMP Customer Premises Equipment (CPE) implementation is not tested to be _fully compliant_ with the TR-069 specification.

The purpose is, rather, to provide:

1. A basic, OOTB tool for testing a TR-069 ACS (the server side)
2. A flexible and powerful toolkit for CWMP users to devise more targeted testing scenarios
3. A starting point for anyone wishing to build out a more fully-compliant CWMP client
4. A teaching/demo codebase illustrating TR-069 messaging in action

## Features

1. Convenient "lab" of cwmp clients can be pointed at an ACS.
2. REPL interaction to set parameters locally and cause a VALUE_CHANGE inform.
3. Handles instance wildcards in parameter and object names.
4. AddObject support.
5. Basic inform events: BOOTSTRAP, BOOT, PERIODIC, VALUE_CHANGE
6. CPE-side parameters.
7. Connection Request support.
8. Currently divides the `PeriodicInformInterval` by 1000 (!) to cause more frequent interaction.

## How-to

Get usage:

```
./mgr help
```

## Run a horde of CWMP CPE instances

However you run this code, your horde of CWMP CPE instances will show up in the
ACS as devices with MAC addresses numbered counting up from the OUI in your
config file. For example, if your OUI is `FEFEFE` (a [locally-administered
address](https://en.wikipedia.org/wiki/MAC_address#Universal_vs._local_(U/L_bit))),
and you specify `:instance-count 3`, then the following devices will be
registered with your ACS:

```
FEFEFE000000
FEFEFE000001
FEFEFE000002
```

### Run from Docker

You might like to have a docker image of the horde, for testing in a
docker-compose setup or deploying to Kubernetes to have a whole "lab" of CPEs
pointed at an ACS.

First, you probably want to copy example-config.edn and add your ACS URL and
customize as you like.

```
cp -i example-config.edn config.edn # then edit it to add your ACS URL and customize
./mgr build
CONFIG_FILE_PATH=config.edn IMAGE_NAME=cwmp-cpe-horde:20250623_204156-gaadc89d ./mgr up
```

#### Image Tags

The build encoded by `mgr` tags images, by default, with a version that is
understandable by humans, sorts chronologically, and maps directly back to a
git SHA.

For example:
```
20250623_204156-gaadc89d_DIRTY
^------+------^ ^^--+--^-^-+-^
       |        |   |      \ ---- (optional) repo has uncommitted changes
       |        |   \------------ git sha
       |        \ --------------- the letter "g", which stands for "git"[1]
       \------------------------- year/month/day/hour/minute/second of commit
```

See https://github.com/Viasat/lein-voom?tab=readme-ov-file#voom-modifier for prior art.

### Run from Source

Don't forget to copy example-config.edn and add your ACS URL and customize it to your needs!

If you want to run directly from source, on the command line, `./mgr run` is your friend:

```
cp -i example-config.edn config.edn # then edit it to add your ACS URL and customize
CONFIG_FILE_PATH=config.edn ./mgr run
```

## Run in REPL

See the `(comment ...)` at the end of `src/clojure/viasat/cwmp_cpe/main.clj` for some ready-to-go, basic REPL interaction.

## CPE State

### Dump

CPE state can be dumped by using the HTTP API of the horde. For example:

```
$ curl -s localhost:9000/stateful-devices/FEFEFE000000 | jq .
{
  "swap-result": null,
  "spvs": {
    "Device.DeviceInfo.ManufacturerOUI": "FEFEFE",
    "Device.DeviceInfo.HardwareVersion": "cwmp-test-hardware-version",
    "Device.ManagementServer.PeriodicInformInterval": 60,
    "Device.ManagementServer.ConnectionRequestPassword": "pavHnhAlzmE4",
    "Device.DeviceInfo.Manufacturer": "cwmp-test-manufacturer",
    "Device.DeviceInfo.ProvisioningCode": "Default",
    "Device.ManagementServer.ConnectionRequestUsername": "8EMzuErdENsU",
    "Device.ManagementServer.PeriodicInformEnable": true,
    "Device.DeviceInfo.SerialNumber": "FEFEFE000000",
    "Device.DeviceInfo.SoftwareVersion": "software_version",
    "Device.ManagementServer.ConnectionRequestURL": "localhost:9000/cpes/FEFEFE000000",
    "Device.ManagementServer.InstanceWildcardsSupported": true,
    "Device.DeviceInfo.ProductClass": "product-class",
    "Device.ManagementServer.PeriodicInformTime": "1970-01-01T03:52:26Z"
  },
  "spvs-sources": {
    "Device.DeviceInfo.ManufacturerOUI": "cpe",
    "Device.DeviceInfo.HardwareVersion": "cpe",
    "Device.ManagementServer.PeriodicInformInterval": "acs",
    "Device.ManagementServer.ConnectionRequestPassword": "acs",
    "Device.DeviceInfo.Manufacturer": "cpe",
    "Device.DeviceInfo.ProvisioningCode": "acs",
    "Device.ManagementServer.ConnectionRequestUsername": "acs",
    "Device.ManagementServer.PeriodicInformEnable": "acs",
    "Device.DeviceInfo.SerialNumber": "cpe",
    "Device.DeviceInfo.SoftwareVersion": "cpe",
    "Device.ManagementServer.ConnectionRequestURL": "cpe",
    "Device.ManagementServer.InstanceWildcardsSupported": "cpe",
    "Device.DeviceInfo.ProductClass": "cpe",
    "Device.ManagementServer.PeriodicInformTime": "acs"
  },
  "supported-param-names": [
    "Device.Services.X_YZ-COM_Shindig."
  ],
  "object-instances": {
    "Device.Services.X_YZ-COM_Shindig.": {
      "source": "acs",
      "current-index-max": 0
    }
  },
  "cnr-at-millis": null,
  "processor-state": {
    "events": [
      {
        "event-type": "bootstrap",
        "event-time": "2025-07-01T14:44:11Z"
      },
      {
        "event-type": "boot",
        "event-time": "2025-07-01T14:44:11Z"
      },
      {
        "event-type": "connection-request",
        "event-time": "2025-07-01T14:44:23Z"
      },
      {
        "event-type": "periodic-inform",
        "event-time": "2025-07-01T14:54:11Z"
      },
      {
        "event-type": "periodic-inform",
        "event-time": "2025-07-01T15:04:11Z"
      }
    ],
    "latest-inform": "2025-07-01T15:04:11Z"
  }
}
```

### Connection Request (CNR)

Using the dump feature, the ConnectionRequestUsername and ConnectionRequestPassword can be obtained:

```
$ DEVICE_ID=FEFEFE000000
curl -s localhost:9000/stateful-devices/$DEVICE_ID | jq '.spvs | {"username": ."Device.ManagementServer.ConnectionRequestUsername", "password": ."Device.ManagementServer.ConnectionRequestPassword"}'
{
  "username": "8EMzuErdENsU",
  "password": "pavHnhAlzmE4"
}
```

You can copy 'n paste that together to issue a Connection Request:

```
$ curl -i --digest -v -u "8EMzuErdENsU:pavHnhAlzmE4" localhost:9000/cpes/$DEVICE_ID
* Host localhost:9000 was resolved.
* IPv6: ::1
* IPv4: 127.0.0.1
*   Trying [::1]:9000...
* Connected to localhost (::1) port 9000
* Server auth using Digest with user '8EMzuErdENsU'
> GET /cpes/FEFEFE000000 HTTP/1.1
> Host: localhost:9000
> User-Agent: curl/8.5.0
> Accept: */*
>
< HTTP/1.1 401 Unauthorized
HTTP/1.1 401 Unauthorized
< Date: Fri, 27 Jun 2025 21:33:24 GMT
Date: Fri, 27 Jun 2025 21:33:24 GMT
< WWW-Authenticate: Digest realm="TR-069 Connection Request (cwmp-cpe-horde)", qop="auth", nonce="2103accf0285fdf0cc76584744c59c18", opaque=""
WWW-Authenticate: Digest realm="TR-069 Connection Request (cwmp-cpe-horde)", qop="auth", nonce="2103accf0285fdf0cc76584744c59c18", opaque=""
< Content-Length: 12
Content-Length: 12
< Server: Jetty(11.0.24)
Server: Jetty(11.0.24)

<
* Ignoring the response-body
* Connection #0 to host localhost left intact
* Issue another request to this URL: 'http://localhost:9000/cpes/FEFEFE000000'
* Found bundle for host: 0x62f89a67ed40 [serially]
* Can not multiplex, even if we wanted to
* Re-using existing connection with host localhost
* Server auth using Digest with user '8EMzuErdENsU'
> GET /cpes/FEFEFE000000 HTTP/1.1
> Host: localhost:9000
> Authorization: Digest username="8EMzuErdENsU", realm="TR-069 Connection Request (cwmp-cpe-horde)", nonce="2103accf0285fdf0cc76584744c59c18", uri="/cpes/FEFEFE000000", cnonce="ZWU3NjU0YzlkNGU5MWM2YjMxYWE4MmE5MmMyNWY2YjQ=", nc=00000001, qop=auth, response="09dbdc6b4365f64a09f9db45ff143afa", opaque=""
> User-Agent: curl/8.5.0
> Accept: */*
>
< HTTP/1.1 204 No Content
HTTP/1.1 204 No Content
< Date: Fri, 27 Jun 2025 21:33:24 GMT
Date: Fri, 27 Jun 2025 21:33:24 GMT
< Server: Jetty(11.0.24)
Server: Jetty(11.0.24)

<
* Connection #0 to host localhost left intact
```

Or you can put the whole CNR together on the command line as one command:

```
$ DEVICE_ID=FEFEFE000000
$ curl -i --digest -v \
    -u $(curl -s localhost:9000/stateful-devices/$DEVICE_ID \
            | jq -r '.spvs | (."Device.ManagementServer.ConnectionRequestUsername" + ":" + ."Device.ManagementServer.ConnectionRequestPassword")') \
    localhost:9000/cpes/$DEVICE_ID
* Host localhost:9000 was resolved.
* IPv6: ::1
* IPv4: 127.0.0.1
*   Trying [::1]:9000...
* Connected to localhost (::1) port 9000
* Server auth using Digest with user '8EMzuErdENsU'
> GET /cpes/FEFEFE000000 HTTP/1.1
> Host: localhost:9000
> User-Agent: curl/8.5.0
> Accept: */*
>
< HTTP/1.1 401 Unauthorized
HTTP/1.1 401 Unauthorized
< Date: Fri, 27 Jun 2025 21:21:03 GMT
Date: Fri, 27 Jun 2025 21:21:03 GMT
< WWW-Authenticate: Digest realm="TR-069 Connection Request (cwmp-cpe-horde)", qop="auth", nonce="ea49260d870a2ac67824afca1612fda8", opaque=""
WWW-Authenticate: Digest realm="TR-069 Connection Request (cwmp-cpe-horde)", qop="auth", nonce="ea49260d870a2ac67824afca1612fda8", opaque=""
< Content-Length: 12
Content-Length: 12
< Server: Jetty(11.0.24)
Server: Jetty(11.0.24)

<
* Ignoring the response-body
* Connection #0 to host localhost left intact
* Issue another request to this URL: 'http://localhost:9000/cpes/FEFEFE000000'
* Found bundle for host: 0x623348c34d40 [serially]
* Can not multiplex, even if we wanted to
* Re-using existing connection with host localhost
* Server auth using Digest with user '8EMzuErdENsU'
> GET /cpes/FEFEFE000000 HTTP/1.1
> Host: localhost:9000
> Authorization: Digest username="8EMzuErdENsU", realm="TR-069 Connection Request (cwmp-cpe-horde)", nonce="ea49260d870a2ac67824afca1612fda8", uri="/cpes/FEFEFE000000", cnonce="N2M1ZDRmZWU5OGYyY2JiYTg3ZmZjNzU0NmE0YWFlNDg=", nc=00000001, qop=auth, response="0b156d0228ec198693d4c4542e167d75", opaque=""
> User-Agent: curl/8.5.0
> Accept: */*
>
< HTTP/1.1 204 No Content
HTTP/1.1 204 No Content
< Date: Fri, 27 Jun 2025 21:21:03 GMT
Date: Fri, 27 Jun 2025 21:21:03 GMT
< Server: Jetty(11.0.24)
Server: Jetty(11.0.24)

<
* Connection #0 to host localhost left intact
```

## Potential TODOs

* This tool is more like a framework, than a library. This seems OK, and
  perhaps inevitable, in order to have anything that works "OOTB". Also, this is
  in keeping with the pattern of how such things are often delivered: as working
  frameworks into which vendors are expected to insert their own custom
  functionality. HOWEVER, it would be good to have a clean, well-documented
  approach to proprietary extension, as will be required by almost any
  interesting test scenarios.
    * Would it be good to have well-documented hooks with some kind of
      middleware pattern? E.g., `before-set-parameter-values`,
      `after-set-parameter-values`, etc.?
    * Would it be better to refactor away from the "framework" approach, and
      provide an example composition that works OOTB, but with a documented use
      pattern to compose your own somewhere else, and just use this repo as a set
      of libraries?
* Make the `PeriodicInformInterval` multiplier configurable instead of hard-coded to `1/1000`.
* Finish the file-on-disk stateful-device implementation.
* full TR-069 spec compliance
* TR-369 support
    * Note that the Broadband Forum has published an open-source reference implementation for TR-369 already: https://github.com/BroadbandForum/obuspa
* auth -- currently assumes a testing environment in which the ACS does not require authentication
* mTLS
* refactor to avoid using a blocking thread per cwmp-client instance

