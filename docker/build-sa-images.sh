#!/bin/bash
set -e

#
# Build all Storage agent images. Sort non-base image names alphabetically to help users observe build progress
#

DIRS="
    base/base
    base/jre8
    base/jre8-and-mysql-client
    base/python2.7

    ../src/storage-agent
    storage-agent-setup

    ship-aerofs/sa_loader
"

PWD="$(dirname "$0")"

$PWD/../tools/cache/start.sh

for i in ${DIRS}; do
    echo "========================================"
    echo "  Building Docker image ${i}"
    echo "========================================"
    make -C "$PWD/${i}" image
done

echo "Removing untagged images to save space..."
docker rmi `docker images --no-trunc | grep '^<none>' | awk '{print $3}'` 2>/dev/null || true
