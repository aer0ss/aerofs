#!/bin/bash
set -e
#
# Build AeroFS appliance VM. See Ship Enterprise's vm/build.sh for optional command-line arguments.
#

THIS_DIR="$(dirname $0)"

SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh")"

"${THIS_DIR}/../ship/vm/build.sh" cloudinit,preloaded "${SHIP_YML}" "" "${THIS_DIR}/../../out.ship/appliance" $@

rm "${SHIP_YML}"
