#!/bin/bash
set -e

THIS_DIR="$(dirname $0)"

"${THIS_DIR}/build.sh" cloudinit $@
