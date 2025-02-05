#!/usr/bin/env bash

set -euo pipefail

bb -i -e '(->> (str/split (first *input*) #":") (remove (fn [p] (.isAbsolute (io/file p)))) (str/join ":") print)'
