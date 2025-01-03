#!/bin/bash
set -e
#
# Build AeroFS appliance VM. See Ship Enterprise's vm/build.sh for optional command-line arguments.
#

if [ $# -lt 3 ]; then
    echo "Usage: $0 <output-format> <loader-image-name> <output-dir> [optional arguments]"
    echo "       See Ship Enterprise's vm/build.sh for output formats and optional command-line arguments."
    exit 11
fi
OUTPUT="$1"
LOADER="$2"
OUTPUT_DIR="$3"
shift
shift

THIS_DIR="$(dirname $0)"

SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh" $LOADER)"


PROD_ALLOWS_CONFIGURABLE_REGISTRY=1
"${THIS_DIR}/../ship/vm/build.sh" "${OUTPUT}" "${SHIP_YML}" "" "${OUTPUT_DIR}" "${PROD_ALLOWS_CONFIGURABLE_REGISTRY}" $@

rm "${SHIP_YML}"
