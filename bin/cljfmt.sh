#!/usr/bin/env bash

set -euo pipefail
[[ -z ${DEBUG+x} ]] || set -x

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" >& /dev/null && pwd )

function _cljfmt() {
    clojure -Sdeps '{:deps {dev.weavejester/cljfmt {:mvn/version "0.13.1"}}}' -M -m cljfmt.main $@
}

_cljfmt=__cljfmt

files="$@"
# check all modified or untracked files if no file list is provided
all_arg=""
[[ -z ${CLJFMT_ALL+x} ]] || all_arg="--cached"
[[ -z "${files}" ]] && files=$(git ls-files ${all_arg} --modified --others --exclude-standard '*.clj' '*.edn' | grep -v \\.clj-kondo/)
_cljfmt fix ${files}
