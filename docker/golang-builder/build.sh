#!/bin/bash
#
# Usage: build.sh image service
#
#   image  : name of the docker image to be produced
#   service: fully qualified name of the go package to build

IMAGE=$1
SERVICE=$2
SOURCE_MAPPING=$(git rev-parse --show-toplevel)/golang/src/aerofs.com:/gopath/src/aerofs.com
SOCKET_MAPPING=/var/run/docker.sock:/var/run/docker.sock
DOCKER_MAPPING=$(which docker):/usr/local/bin/docker

echo "Building $SERVICE into $IMAGE from $SOURCE_MAPPING"
docker run --rm -v $SOCKET_MAPPING -v $DOCKER_MAPPING -v $SOURCE_MAPPING \
    aerofs/golang-builder $IMAGE $SERVICE
