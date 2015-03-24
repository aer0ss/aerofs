#!/bin/bash
set -e
#
# Render ship.yml and run Ship Enterprise's push-images
#

THIS_DIR="$(dirname $0)"
SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh")"

"${THIS_DIR}/../ship/push-images.sh" "${SHIP_YML}"

rm "${SHIP_YML}"
