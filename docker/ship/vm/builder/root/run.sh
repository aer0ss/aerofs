#!/bin/bash
set -ex
#
# Ideally all the commands here should be in Dockerfile instead to leverage Docker's build cache.
# Unfortunately losetup & mount commands require privileged access which is currently unsupported:
# https://github.com/docker/docker/issues/1916.
#

if [ $# != 3 ]; then
    echo "Usage: $0 <path_to_ship.yml> <path_to_output_folder> <tag>"
    exit 11
fi
SHIP_YML="$1"
OUT="$2"
TAG="$3"

DIR=$(dirname "${BASH_SOURCE[0]}")

# Generate cloud-config files
mkdir -p "${OUT}/preloaded"
"${DIR}/render-cloud-configs.py" "${SHIP_YML}" "${OUT}/cloud-config.yml" "${OUT}/preload-cloud-config.yml" "${TAG}"

# Generate preloaded VDI image
"${DIR}/inject.sh" /coreos.bin "${OUT}/preload-cloud-config.yml" cloud-config.yml
qemu-img convert -O vdi /coreos.bin "${OUT}/preloaded/disk.vdi"

# Skip bare image generation for now

# If $OUT is a shared volume with the host, files & folders written to this folder might belong to the host's root user.
# We allow everyone to write them so later build steps that run as non-root may succeed.
# Don't use umask as some commands don't respect it.
chmod -R a+w "${OUT}"
