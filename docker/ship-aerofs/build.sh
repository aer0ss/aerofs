#!/bin/bash
set -e
#
# Build AeroFS appliance VM. See Ship Enterprise's vm/build.sh for optional command-line arguments.
#

if [ $# != 1 ]; then
    echo "Usage: $0 <output-format> [optional arguments]"
    echo "       See Ship Enterprise's vm/build.sh for output formats and optional command-line arguments."  
    exit 11
fi
OUTPUT="$1"
shift

THIS_DIR="$(dirname $0)"

SHIP_YML="$("${THIS_DIR}/render-ship-yml.sh")"

"${THIS_DIR}/../ship/vm/build.sh" "${OUTPUT}" "${SHIP_YML}" "" "${THIS_DIR}/../../out.ship/appliance" $@

rm "${SHIP_YML}"
