#!/bin/bash
set -e

[[ $# -eq 2 ]] || {
    echo "Usage: $0 <image> <service>"
    echo "      <image>   name of the docker image to be produced"
    echo "      <service> fully qualified name of the go package to build. e.g. 'aerofs.com/ca-server'"
    exit 11
}
IMAGE=$1
SERVICE=$2

BUILDER=aerofs/golang-builder
echo "Building ${BUILDER} ..."

# Build inside golang folder for COPY to work
THIS_DIR="$(dirname "$0")"
SRC_DIR="${THIS_DIR}/.."

docker build -t ${BUILDER} -f "${THIS_DIR}/Dockerfile" "${SRC_DIR}"

echo "Building container image ${IMAGE} ..."
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${BUILDER} ${IMAGE} ${SERVICE}

