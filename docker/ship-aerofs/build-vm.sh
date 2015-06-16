#!/bin/bash
set -eu

THIS_DIR="$(dirname $0)"

"${THIS_DIR}/build.sh" cloudinit,preloaded $@

# Convert to absolute path for user friendly display
OUTPUT_DIR="$(cd "${THIS_DIR}/../../out.ship/appliance/preloaded"; pwd)"

BASENAME=$(basename "${OUTPUT_DIR}"/*.ova | sed -e 's/\.ova//')

QCOW2="${OUTPUT_DIR}/${BASENAME}.qcow2"
echo "Creating QCOW2 image ${QCOW2} ..."
qemu-img convert -O qcow2 "${OUTPUT_DIR}/${BASENAME}.ova" "${QCOW2}"

VMDK="${OUTPUT_DIR}/${BASENAME}.vmdk"
echo "Creating VMDK image ${VMDK} ..."
qemu-img convert -O vmdk "${OUTPUT_DIR}/${BASENAME}.ova" "${VMDK}"

# Remove irrelevant build artifacts.
rm -f "${OUTPUT_DIR}/disk.vdi"
