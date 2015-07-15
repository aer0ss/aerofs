#!/bin/bash
set -e
#
# Render ship.yml and run Ship Enterprise's push-images
#
# TODO: this doesn't pull 'common', so we copy the webhook value:
readonly SLACK_WEBHOOK="https://hooks.slack.com/services/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"

THIS_DIR="$(dirname $0)"
SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh")"

"${THIS_DIR}/../ship/push-images.sh" "${SHIP_YML}"

rm "${SHIP_YML}"

echo "Notifying Slack ..."
VERSION=$(docker run --rm aerofs/loader tag)

for room in "#eng" "#success"
do
    echo "Release notification: Docker images version ${VERSION} pushed to registry.aerofs.com (by $(whoami)). This version is immediately available to the public." |
        $(git rev-parse --show-cdup)puppetmaster/modules/slack/files/slack_message \
            -r "$room" \
            -c good \
            -u $SLACK_WEBHOOK \
            -f "Build" > /dev/null
done
