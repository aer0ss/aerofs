#!/bin/bash -e
chroot /mnt/image /opt/installers/tools/pull.sh
echo "Client binaries successfully downloaded."

# Stop pesky services so we can unmount successfully.
chroot /mnt/image service restund stop || true
chroot /mnt/image service sanity stop || true
