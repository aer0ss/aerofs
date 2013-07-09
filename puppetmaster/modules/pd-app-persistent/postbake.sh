#!/bin/bash -e

# Stop pesky services so we can unmount successfully.
for service in ca-server sanity php5-fpm
do
    chroot /mnt/image service $service stop || true
done
