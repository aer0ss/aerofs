#!/bin/bash
set -eu

# Build OVA, remove build artifacts, change to output directory.
THIS_DIR="$(dirname $0)"
"${THIS_DIR}/build.sh" cloudinit,preloaded $@
cd "${THIS_DIR}/../../out.ship/appliance/preloaded"
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
