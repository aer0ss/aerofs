#!/bin/bash
set -e

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"

# Rebuild Loader to incorporate any changes on crane.yml
make -C "${THIS_DIR}/../ship-aerofs/loader"

SHIP_YML="$("${THIS_DIR}/../ship-aerofs/render-ship-yml.sh" aerofs/loader)"

"${THIS_DIR}/../ship/emulate-rm.sh" "${SHIP_YML}"

rm -f "${SHIP_YML}"
