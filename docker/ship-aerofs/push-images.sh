#!/bin/bash
set -e
#
# Render ship.yml and run Ship Enterprise's push-images
#
# TODO: this doesn't pull 'common', so we copy the webhook value:
readonly SLACK_WEBHOOK="https://hooks.slack.com/services/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"

THIS_DIR="$(dirname $0)"
SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh" $1)"

"${THIS_DIR}/../ship/push-images.sh" "${SHIP_YML}"

rm "${SHIP_YML}"

if [ "$1" == "aerofs/loader" ]; then
     IMGS_FOR="AeroFS appliance"
else
     IMGS_FOR="AeroFS storage agent"
fi
echo "Notifying Slack ..."
VERSION=$(docker run --rm $1 tag)

for room in "#success"
do
    echo "Release notification: ${IMGS_FOR} docker images version ${VERSION} pushed to registry.aerofs.com (by $(whoami)). This version is immediately available to the public." |
        $(git rev-parse --show-cdup)puppetmaster/modules/slack/files/slack_message \
            -r "$room" \
            -c good \
            -u $SLACK_WEBHOOK \
            -f "Build" > /dev/null
done
