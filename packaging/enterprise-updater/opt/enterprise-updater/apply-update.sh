#!/bin/bash -e

# Copy installers into installer directory and remove the repackage done file to
# force repackaging.
cp /opt/enterprise-updater/installers/* /opt/installers/binaries/original
rm -f "/opt/installers/binaries/modified/.repackage-done"

# Manually install all updates. Corresponds to what is packaged by the builder
# script.
cd /opt/enterprise-updater/debians

for package in web repackaging sp bootstrap
do
    sudo dpkg -i aerofs-${package}.deb
done
