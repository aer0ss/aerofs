#!/bin/bash -e

# This script must be run to configure the server in order to pull all the aerofs images

REPO='registry.aerofs.com'

# Pull the latest loader from the registry and get its tag (version number)
docker pull $REPO/aerofs/loader:latest
TAG=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock $REPO/aerofs/loader:latest tag)


# Pull the images
# TODO: Don't pull images that are not needed in HPC
IMAGES=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock $REPO/aerofs/loader images)
for i in ${IMAGES}; do
    IMAGE="$REPO/${i}:$TAG"
    set +e; docker inspect "${IMAGE}" 1>/dev/null 2>/dev/null; EXIT=$?; set -e
    [[ ${EXIT} = 0 ]] || docker pull "${IMAGE}"
done

