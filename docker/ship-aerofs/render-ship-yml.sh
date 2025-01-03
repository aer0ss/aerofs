#!/bin/bash
set -e

LOADER_IMAGE=$1
# Default value of registry.aerofs.com. So if only one argument is passed to this script
# use registry.aerofs.com for REPO
REPO=${2:-registry.aerofs.com}

TAG="$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_IMAGE} tag)"
THIS_DIR="$(dirname ${BASH_SOURCE[0]})"
SHIP_YML="$(mktemp -t ship-aerofs-XXX)"

if [ "$LOADER_IMAGE" == "aerofs/loader" ]; then
    APPLIANCE="aerofs-appliance"
    DISK_SIZE="51200"
    RAM_SIZE="4096"
else
    APPLIANCE="aerofs-sa-appliance"
    DISK_SIZE="153600"
    RAM_SIZE="1024"
fi
sed -e "s,{{ tag }},${TAG}," \
    -e "s,{{ repo }},${REPO}," \
    -e "s,{{ loader }},${LOADER_IMAGE}," \
    -e "s,{{ appliance }},${APPLIANCE}," \
    -e "s,{{ disk-size }},${DISK_SIZE}," \
    -e "s,{{ ram-size }},${RAM_SIZE}," \
    "${THIS_DIR}/ship.yml.jinja" > "${SHIP_YML}"

echo "${SHIP_YML}"
