#!/bin/bash
set -eu

THIS_DIR="$( cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )"

source "${THIS_DIR}/../../golang/gockerize/img_fresh.sh"

img_fresh "$1" "$2"
