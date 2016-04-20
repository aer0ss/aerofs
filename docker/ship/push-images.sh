#!/bin/bash
set -e

if [ $# != 1 ]; then
    echo "Usage: $0 <path_to_ship.yml>"
    exit 11
fi

SHIP_YML="$1"
THIS_DIR="$(dirname $0)"

LOADER_IMAGE=$(${THIS_DIR}/yml.sh ${SHIP_YML} 'loader')

TAG=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_IMAGE} tag)

PUSH_REPO=$(${THIS_DIR}/yml.sh ${SHIP_YML} 'push-repo')
if [ -z "${PUSH_REPO}" ]; then
    PUSH_REPO=$(${THIS_DIR}/yml.sh ${SHIP_YML} 'repo')
fi

# Add repo name only when pushing to aerofs registry. For pushing images
# to docker hub adding repo name(registry.hub.docker.com) complains of
# "unauthorized: access to the requested resource is not authorized".
if [[ $PUSH_REPO == *"aerofs"* ]]; then
    PUSH_IMAGE_PREFIX="${PUSH_REPO}/"
else
    PUSH_IMAGE_PREFIX=""
fi

# Tag all images with the next latest version and push it to the registry.
# Images pushed to the registry are not immediately available to the public
# until the loader image is tag with "latest".
for i in $(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_IMAGE} images); do
    echo "============================================================"
    echo " Pushing ${i}:${TAG} to ${PUSH_REPO}..."
    echo "============================================================"
    PUSH_IMAGE="${PUSH_IMAGE_PREFIX}${i}:${TAG}"
    docker tag -f "${i}" "${PUSH_IMAGE}"
    docker tag -f "${i}" "${PUSH_IMAGE_PREFIX}${i}:latest"
    ${THIS_DIR}/push-docker-image.sh "${PUSH_IMAGE}"
    docker rmi "${PUSH_IMAGE}"
done

