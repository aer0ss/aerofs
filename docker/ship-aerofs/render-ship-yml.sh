#!/bin/bash
set -e

LOADER_IMAGE=aerofs/loader
TAG="$(docker run --rm ${LOADER_IMAGE} tag)"
THIS_DIR="$(dirname ${BASH_SOURCE[0]})"
SHIP_YML="$(mktemp -t ship-aerofs-XXX)"

sed -e "s,{{ tag }},${TAG}," \
    -e "s,{{ loader }},${LOADER_IMAGE}," \
    "${THIS_DIR}/ship.yml.jinja" > "${SHIP_YML}"

echo "${SHIP_YML}"