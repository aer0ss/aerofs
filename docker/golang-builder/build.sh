#!/bin/bash

[[ $# = 2 ]] || {
    echo "Usage: $0 <image> <service>"
    echo "      <image>   name of the docker image to be produced"
    echo "      <service> fully qualified name of the go package to build"
    exit 11
}
IMAGE=$1
SERVICE=$2

THIS_DIR="$(dirname "$0")"

BUILDER_IMAGE=aerofs/golang-builder
echo "Building ${BUILDER_IMAGE} ..."
docker build -t ${BUILDER_IMAGE} "${THIS_DIR}"

echo "Building ${SERVICE} into ${IMAGE} ..."
SOURCE_MAPPING=$(git rev-parse --show-toplevel)/golang/src/aerofs.com:/gopath/src/aerofs.com
SOCKET_MAPPING=/var/run/docker.sock:/var/run/docker.sock
docker run --rm \
    -v ${SOURCE_MAPPING} \
    -v ${SOCKET_MAPPING} \
    ${BUILDER_IMAGE} ${IMAGE} ${SERVICE}
