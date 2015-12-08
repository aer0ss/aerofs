#!/bin/bash
#
# Remove containers created by emulate.sh
#
set -e

if [ $# != 1 ]; then
    echo "Usage: $0 <path-to-ship.yml>"
    exit 11
fi
SHIP_YML="$1"

yml() {
    grep "^$1:" "${SHIP_YML}" | sed -e "s/^$1: *//" | sed -e 's/ *$//'
}

LOADER_IMAGE=$(yml 'loader')

# Container name must be identical to the name defined in emulate.sh
CONTAINER=loader

echo "Getting container list ..."
for i in $(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_IMAGE} containers) ${CONTAINER}; do
    echo "Removing ${i} ..."
    # Ignore `docker rm` errors
    set +e
    docker rm -vf ${i} 2>/dev/null
    set -e
done
