#!/bin/bash
set -e

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"

# Rebuild SA Loader to incorporate any changes on crane.yml
make -C "${THIS_DIR}/../ship-aerofs/sa-loader"

${THIS_DIR}/modify-sa-loader.sh

SHIP_YML="$("${THIS_DIR}/../ship-aerofs/render-ship-yml.sh" aerofs/sa-loader)"

"${THIS_DIR}/../ship/emulate-rm.sh" "${SHIP_YML}" sa-loader

rm -f "${SHIP_YML}"
