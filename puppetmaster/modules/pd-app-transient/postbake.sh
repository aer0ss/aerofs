#!/bin/bash -e
cd /mnt/image/opt/web/web/static
./pull_installers.sh
echo "Client binaries successfully downloaded."

# Stop pesky services so we can unmount successfully.
chroot /mnt/image service restund stop || true
