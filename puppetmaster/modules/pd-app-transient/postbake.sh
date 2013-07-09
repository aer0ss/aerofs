#!/bin/bash -e

# Stop pesky services so we can unmount successfully.
for service in \
    tomcat6 \
    restund \
    sanity \
    verkehr \
    zephyr \
    ejabberd
do
    chroot /mnt/image service $service stop || true
done

# The ejabberd service script should do this, but alas, it sucks.
chroot /mnt/image killall -9 epmd

chroot /mnt/image /opt/installers/tools/pull-binaries.sh
echo "Client binaries successfully downloaded."
