#!/bin/bash

[[ $# = 1 ]] || { echo "Usage: $0 <ip>"; exit 11; }

THIS_DIR="$(dirname $0)"

${THIS_DIR}/../../webdriver-lib/test-wrapper.sh ${THIS_DIR} "$1"
