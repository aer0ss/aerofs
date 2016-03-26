#!/bin/bash
set -ex

THIS_DIR="$(dirname $0)"

# Build Loader base image
${THIS_DIR}/../ship/vm/loader/build.sh

# Build all containers
for i in loader nginx registry; do
    docker build -t aerofs/registry.${i} "${THIS_DIR}/${i}"
done

# Verify Loader
docker run --rm aerofs/registry.loader verify aerofs/registry.loader

"${THIS_DIR}/../ship/vm/build.sh" cloudinit "${THIS_DIR}/ship.yml" "${THIS_DIR}/keys" "${THIS_DIR}/../../out.ship/registry" $@
