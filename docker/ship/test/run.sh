#!/bin/bash
set -e

[[ $# = 2 ]] || {
    echo "Usage: $0 <aws_access_key> <aws_secret_key>"
    exit 11
}
DOCKER_USER="aerofs"
AWS_ACCESS_KEY="$2"
AWS_SECRET_KEY="$3"

THIS_DIR="$(dirname "$0")"

echo ">>> Building and publishing the app under test ..."
"${THIS_DIR}"/app/build_and_push_images.sh "${DOCKER_USER}"

echo ">>> Building test driver ..."
mkdir -p "${THIS_DIR}"/driver/root
cp "${THIS_DIR}"/app/build/ship/cloud-config.yml "${THIS_DIR}"/driver/root/build
docker build -t shipenterprise/test-driver "${THIS_DIR}"/driver

echo ">>> Running driver drive ..."
docker run --rm shipenterprise/test-driver /test.sh "${AWS_ACCESS_KEY}" "${AWS_SECRET_KEY}"
