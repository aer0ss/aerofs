#!/bin/bash
set -ex
#
# This script runs inside the guest VM as root
#

if [ $# != 3 ]; then
    echo "Usage: $0 <preload_repo> <loader_image> <repo>"
    exit 11
fi
PRELOAD_REPO=$1
LOADER_IMAGE=$2
REPO=$3

if [ $(whoami) != root ]; then
    echo "ERROR: please run $0 as root."
    exit 22
fi

# Add a drop-in to allow pulling from the insecure preload repo
DROP_IN=/etc/systemd/system/docker.service.d/50-insecure-preload-registry.conf
mkdir -p $(dirname ${DROP_IN})
cat >  ${DROP_IN} <<< "[Service]"
cat >> ${DROP_IN} <<< "Environment=DOCKER_OPTS='--insecure-registry=\"${PRELOAD_REPO}\"'"
systemctl daemon-reload
systemctl restart docker.service

# Surpress stdout otherwise for some reason pulling is extremely slow (20+ images can take several hours).
pull_image() {
    docker pull "$1" > /dev/null
}

# Fetch and verify image list
LOADER="${PRELOAD_REPO}/${LOADER_IMAGE}"
pull_image "${LOADER}"
IMAGES=$(docker run --rm "${LOADER}" images)
TAG=$(docker run --rm "${LOADER}" tag)
if [ x"$(echo "${IMAGES}" | grep "${LOADER_IMAGE}")" = x ]; then
    echo "ERROR: ${LOADER_IMAGE} not found in its own image list."
    exit 22
fi

# Pull all the images
for i in ${IMAGES}; do
    pull_image "${PRELOAD_REPO}/${i}"
done

# Remove drop-in
rm ${DROP_IN}
systemctl daemon-reload
systemctl restart docker.service

# Remove repo name and add tag to the images
for i in ${IMAGES}; do
    docker tag "${PRELOAD_REPO}/${i}" "${REPO}/${i}:${TAG}"
    docker rmi "${PRELOAD_REPO}/${i}"
done

# Create the loader tag which will be used by the sail script.
docker tag "${REPO}/${LOADER_IMAGE}:${TAG}" "${REPO}/${LOADER_IMAGE}"
