#!/bin/bash
set -eu

LOADER="$1"
shift
if [ "$LOADER" == "aerofs/loader" ]; then
    OUTPUT_DIR="${THIS_DIR}/../../out.ship/appliance"
else
    OUTPUT_DIR="${THIS_DIR}/../../out.ship/sa-appliance"
fi

# Build OVA, remove build artifacts, change to output directory.
THIS_DIR="$(dirname $0)"
"${THIS_DIR}/build.sh" cloudinit,preloaded "$LOADER" "$OUTPUT_DIR" $@

cd "${OUTPUT_DIR}/preloaded"
rm -f "disk.vdi"
BASENAME=$(basename *.ova | sed -e 's/\.ova//')

# Build VMDK.
VMDK="${BASENAME}.vmdk"
echo "Creating VMDK image ${VMDK} ..."
tar -xvf ${BASENAME}.ova
mv ${BASENAME}-disk1.vmdk ${BASENAME}.vmdk
rm -f ${BASENAME}.mf ${BASENAME}.ovf

# Build VHD.
VHD="${BASENAME}.vhd"
echo "Creating VHD image ${VHD} ..."
qemu-img convert -f vmdk -O vpc "${VMDK}" "${VHD}"
gzip "${VHD}"

# Build QCow2.
QCOW2="${BASENAME}.qcow2"
echo "Creating QCow2 image ${QCOW2} ..."
qemu-img convert -c -f vmdk -O qcow2 "${VMDK}" "${QCOW2}"
rm -f "${VMDK}"

# Success!
echo "Images successfully built."
