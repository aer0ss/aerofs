#!/bin/bash
set -e

# Copy installers into installer directory and remove the repackage done file to
# force repackaging.
rm -rf /opt/repackaging/installers
mkdir -p /opt/repackaging/installers/original
mkdir -p /opt/repackaging/installers/modified
cp -av /opt/enterprise-updater/installers/* /opt/repackaging/installers/original/

# Manually install all updates. Corresponds to what is packaged by the builder
# script.
cd /opt/enterprise-updater/debians

for package in web repackaging sp bootstrap
do
    echo "Installing aerofs-${package}.deb..."
    sudo dpkg -i aerofs-${package}.deb
done

echo "SUCCESS!"
