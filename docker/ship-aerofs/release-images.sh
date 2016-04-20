#!/bin/bash
set -eu
#
# Render ship.yml and run Ship Enterprise's release-images
#

THIS_DIR="$(dirname $0)"
# We expect $1 to be the loader name and $2 to be the registry we are pushing to.
SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh" $1 $2)"

${THIS_DIR}/../ship/release-images.sh ${SHIP_YML}

rm "${SHIP_YML}"

${THIS_DIR}/notify-slack.sh $1 $2 release