#!/bin/bash
set -ex
#
# Ideally all the commands here should be in Dockerfile instead to leverage Docker's build cache.
# Unfortunately losetup & mount commands require privileged access which is currently unsupported:
# https://github.com/docker/docker/issues/1916.
#

if [ $# != 2 ]; then
    echo "Usage: $0 <path_to_ship.yml> <path_to_output_folder>"
    exit 11
fi
SHIP_YML="$1"
OUT="$2"

DIR=$(dirname "${BASH_SOURCE[0]}")

# Generate cloud-config files
mkdir -p "${OUT}/preloaded"
"${DIR}/render-cloud-configs.py" "${SHIP_YML}" "${OUT}/cloud-config.yml" "${OUT}/preload-cloud-config.yml"

# Generate preloaded VDI image
"${DIR}/inject.sh" /coreos.bin "${OUT}/preload-cloud-config.yml" cloud-config.yml
qemu-img convert -O vdi /coreos.bin "${OUT}/preloaded/disk.vdi"

# Skip bare image generation for now
