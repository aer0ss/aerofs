#!/bin/bash
set -e

THIS_DIR="$(dirname $0)"

"${THIS_DIR}/build.sh" cloudinit,preloaded $@

# Convert to absolute path for user friendly display
OUTPUT_DIR="$(cd "${THIS_DIR}/../../out.ship/appliance/preloaded"; pwd)"

BASENAME=$(basename "${OUTPUT_DIR}"/*.ova | sed -e 's/\.ova//')
QCOW2="${OUTPUT_DIR}/${BASENAME}.qcow2"

echo "Creating QCOW2 image ${QCOW2} ..."

qemu-img convert -O qcow2 "${OUTPUT_DIR}/${BASENAME}.ova" "${QCOW2}"