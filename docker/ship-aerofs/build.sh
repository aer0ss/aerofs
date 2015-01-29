#!/bin/bash
set -ex
#
# See ../ship/vm/builder/build.sh for optional command-line arguments
#

LOADER_IMAGE=aerofs/loader
TAG="$(docker run --rm ${LOADER_IMAGE} tag)"
SHIP_YML="$(mktemp -t ship-aerofs-XXX)"
THIS_DIR="$(dirname ${BASH_SOURCE[0]})"

sed -e "s,{{ tag }},${TAG}," \
    -e "s,{{ loader }},${LOADER_IMAGE}," \
    "${THIS_DIR}/ship.yml.jinja" > "${SHIP_YML}"

"${THIS_DIR}/../ship/vm/builder/build.sh" "${SHIP_YML}" "${THIS_DIR}/../../out.ship" $@

rm "${SHIP_YML}"
