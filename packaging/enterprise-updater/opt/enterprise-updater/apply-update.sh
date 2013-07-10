#!/bin/bash -e

# Copy installers into installer directory and remove the repackage done file to
# force repackaging.
cp /opt/enterprise-updater/installers/* /opt/installers/binaries/original
rm -f "/opt/installers/binaries/modified/.repackage-done"

# Manually install all updates.
for deb in $(ls /opt/enterprise-updater/debians/*)
do
    sudo dpkg -i $deb
done
