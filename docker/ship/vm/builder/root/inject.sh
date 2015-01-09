#!/bin/bash
set -ex
#
# Inject the given file to the root folder of a CoreOS image's OEM partition.
#
# See https://github.com/coreos/docs/blob/a18d3605f85d20df552696e90903097a4de01650/sdk-distributors/distributors/notes-for-distributors/index.md
# for a reference. Note that the OEM overriding instructions have been remove from later versions.

if [ $# != 3 ]; then
    echo "Usage: $0 <path_to_coreos.bin> <path_to_injected_file> <target_name_of_injected_file>"
    exit 11
fi

IMAGE="$1"
FILE="$2"
FILE_NAME="$3"

PARTITION=$(partx -gn6 /coreos.bin)
if [ $(echo "${PARTITION}" | awk '{print $6}') != 'OEM' ]; then
    echo "ERROR: Partition 6 is not the OEM partition."
    exit 22
fi

SECTOR_START=$(echo "${PARTITION}" | awk '{print $2}')
SECTOR_SIZE=$(blockdev --getss $(losetup --find --show "${IMAGE}" && losetup -D))
OFFSET=$(expr ${SECTOR_START} \* ${SECTOR_SIZE})

# Due to AUFS quirks the original coreos.bin can be only mounted read-only
mv /coreos.bin /coreos.bin.2
mv /coreos.bin.2 /coreos.bin

# Ideally we should simply "losetup --find -P image && mount /dev/loopNp6 /mnt" but for some reason
# it doesn't work.
mount -o loop,offset=${OFFSET} "${IMAGE}" /mnt
rm -rf /mnt/*
cp "${FILE}" "/mnt/${FILE_NAME}"
umount /mnt

# Verify
mount -o loop,offset=${OFFSET} "${IMAGE}" /mnt
diff "${FILE}" "/mnt/${FILE_NAME}"
DIFF_STATUS=$?
umount /mnt

if [ ${DIFF_STATUS} != 0 ]; then
    echo "ERROR: verification of the injected file failed"
    exit 33
fi
