#!/bin/bash
#
# Usage: build.sh image service [source [mapping]]
#
#   image  : name of the docker image to be produced
#   service: fully qualified name of the go package to build
#   source : source folder
#            default: $PWD/src
#   mapping: where to mount the above folder in $GOPATH/src
#            default: <service>
#            useful if the source folder contains multiple top-level packages

IMAGE=$1
SERVICE=$2
SOURCE_MAPPING=${3:-$(pwd)/src}:/gopath/src/${4:-$SERVICE}
SOCKET_MAPPING=/var/run/docker.sock:/var/run/docker.sock
DOCKER_MAPPING=$(which docker):/usr/local/bin/docker

echo "Building $SERVICE into $IMAGE from $SOURCE_MAPPING"
docker run --rm -v $SOCKET_MAPPING -v $DOCKER_MAPPING -v $SOURCE_MAPPING \
    aerofs/golang-builder $IMAGE $SERVICE
