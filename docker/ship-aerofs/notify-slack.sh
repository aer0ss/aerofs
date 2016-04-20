#!/bin/bash
set -eu

# TODO: this doesn't pull 'common', so we copy the webhook value:
readonly SLACK_WEBHOOK="https://hooks.slack.com/services/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"

if [ "${3:-unset}" == "release" ]; then
    IS_AVAILABLE='This version is immediately available to the public'
else
    IS_AVAILABLE='This version is not yet available to the public.'
fi

if [ "$1" == "aerofs/loader" ]; then
     IMGS_FOR="AeroFS appliance"
else
     IMGS_FOR="AeroFS storage agent"
fi

DOCKER_REGISTRY=$2

echo "Notifying Slack ..."
VERSION=$(docker run --rm $1 tag)

for room in "#success"
do
    echo "Release notification: ${IMGS_FOR} docker images version ${VERSION} pushed to ${DOCKER_REGISTRY} (by $(whoami)). ${IS_AVAILABLE}." |
        $(git rev-parse --show-cdup)puppetmaster/modules/slack/files/slack_message \
            -r "$room" \
            -c good \
            -u $SLACK_WEBHOOK \
            -f "Build" > /dev/null
done