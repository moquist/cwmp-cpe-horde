# CWMP Client Horde

TR-069 (AKA "CPE WAN Management Protocol", or CWMP) is a standardized protocol for managing edge devices over a WAN. See https://www.broadband-forum.org/pdfs/tr-069-1-6-1.pdf for the latest (_caveat lector_) specification document.

This CWMP client implementation is not tested to be _fully compliant_ with the TR-069 specification.

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

Run a horde of CWMP clients:

```
# first, copy example-config.edn and add your ACS URL and customize as you like
CONFIG_FILE_PATH=config.edn ./mgr run
```

Your horde of CWMP clients will show up in the ACS as devices with MAC addresses numbered counting up from the OUI in your config file.
For example, if your OUI is `FEFEFE` and you specify `:instance-count 3`, then the following devices will be registered with your ACS:

```
FEFEFE000000
FEFEFE000001
FEFEFE000002
```

## REPL

See the `(comment ...)` at the end of `src/clojure/com/viasat/git/moquist/cwmp_client/main.clj` for some ready-to-go, basic REPL interaction.

## Potential TODOs

* Make the `PeriodicInformInterval` multiplier configurable instead of hard-coded to `1/1000`.
* Finish the file-on-disk stateful-device implementation.
* full TR-069 spec compliance
* TR-369 support
    * Note that the Broadband Forum has published an open-source reference implementation for TR-369 already: https://github.com/BroadbandForum/obuspa
* auth -- currently assumes a testing environment in which the ACS does not require authentication
* mTLS
* refactor to avoid using a blocking thread per cwmp-client instance

