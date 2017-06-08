#!/bin/bash
set +e

THIS_DIR="$(dirname $0)"

LOADER_IMAGE=$(${THIS_DIR}/yml.sh $1 'loader')
PUSH_REPO=$(${THIS_DIR}/yml.sh $1 'push-repo')

if [[ $PUSH_REPO == *"aerofs"* ]]; then
    PUSH_IMAGE_PREFIX="${PUSH_REPO}/"
else
    PUSH_IMAGE_PREFIX=""
fi

TAG=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_IMAGE} tag)

# This will tag the loader image with the "latest" tag and push the
# tag to the registry. Once the image is pushed, the new version
# will be available to the public.

echo "============================================================"
echo " Pushing ${LOADER_IMAGE}:latest to ${PUSH_REPO}..."
echo "============================================================"
LOADER_PUSH_IMAGE=$PUSH_IMAGE_PREFIX${LOADER_IMAGE}
docker tag ${LOADER_IMAGE} ${LOADER_PUSH_IMAGE}
${THIS_DIR}/push-docker-image.sh ${LOADER_PUSH_IMAGE}
docker rmi ${LOADER_PUSH_IMAGE}

echo
echo ">>> New version successfully released: ${TAG}"
echo
