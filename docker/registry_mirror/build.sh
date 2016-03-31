#!/bin/bash
set -eu

THIS_DIR="$(dirname $0)"

# Build Loader base image
make -C "${THIS_DIR}/../ship-aerofs/loader"

# Build all containers
for i in loader nginx registry registry-maintainer; do
    docker build -t aerofs/registry-mirror.${i} "${THIS_DIR}/${i}"
done

# Verify Loader
docker run --rm aerofs/registry-mirror.loader verify aerofs/registry-mirror.loader

PROD_ALLOWS_CONFIGURABLE_REGISTRY=0
"${THIS_DIR}/../ship/vm/build.sh" cloudinit "${THIS_DIR}/ship.yml" "${THIS_DIR}/keys" "${THIS_DIR}/../../out.ship/registry_mirror" "${PROD_ALLOWS_CONFIGURABLE_REGISTRY}" $@
