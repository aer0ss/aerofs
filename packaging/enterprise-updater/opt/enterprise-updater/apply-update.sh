#!/bin/bash -e

# Copy installers into installer directory and remove the repackage done file to
# force repackaging.
cp /opt/enterprise-updater/installers/* /opt/repackaging/installers/original
rm -f "/opt/repackaging/installers/modified/.repackage-done"

# Manually install all updates. Corresponds to what is packaged by the builder
# script.
cd /opt/enterprise-updater/debians

for package in web repackaging sp bootstrap
do
    sudo dpkg -i aerofs-${package}.deb
done
