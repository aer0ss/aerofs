#!/bin/bash
set -e
#
# Render ship.yml and run Ship Enterprise's push-images
#

THIS_DIR="$(dirname $0)"
SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh")"

"${THIS_DIR}/../ship/push-images.sh" "${SHIP_YML}"

rm "${SHIP_YML}"

echo "Notifying Slack ..."
VERSION=$(docker run --rm aerofs/loader tag)

for room in "#eng" "#success"
do
    echo "Build notification: Docker images version ${VERSION} pushed to registyr.aerofs.com (by $(whoami)). This version is immediately available to the public." |
        $(git rev-parse --show-cdup)puppetmaster/modules/slack/files/slack_message \
            -r "$room" \
            -c good \
            -u $SLACK_WEBHOOK \
            -f "Build" > /dev/null
done