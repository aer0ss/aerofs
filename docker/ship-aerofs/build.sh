#!/bin/bash
set -e
#
# Build AeroFS appliance VM. See Ship Enterprise's vm/build.sh for optional command-line arguments.
#

if [ $# != 2 ]; then
    echo "Usage: $0 <output-format> <loader-image-name> [optional arguments]"
    echo "       See Ship Enterprise's vm/build.sh for output formats and optional command-line arguments."
    exit 11
fi
OUTPUT="$1"
LOADER="$2"
shift
shift

THIS_DIR="$(dirname $0)"

SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh" $LOADER)"

if [ "$LOADER" == "aerofs/loader" ]; then
    OUTPUT_DIR="${THIS_DIR}/../../out.ship/appliance"
else
    OUTPUT_DIR="${THIS_DIR}/../../out.ship/sa-appliance"
fi

"${THIS_DIR}/../ship/vm/build.sh" "${OUTPUT}" "${SHIP_YML}" "" "${OUTPUT_DIR}" $@

rm "${SHIP_YML}"
