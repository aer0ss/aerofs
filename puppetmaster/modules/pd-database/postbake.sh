#!/bin/bash -e

# Stop pesky services so we can unmount successfully.
chroot /mnt/image service sanity stop || true
