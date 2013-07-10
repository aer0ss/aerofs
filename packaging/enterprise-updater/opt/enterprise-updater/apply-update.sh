#!/bin/bash -e

# Copy installers into installer directory and remove the repackage done file to
# force repackaging.
cp /opt/enterprise-updater/installers/* /opt/installers/binaries/original
rm -f "/opt/installers/binaries/modified/.repackage-done"

# Manually install all updates. Corresponds to what is packaged by the builder
# script.

cd /opt/enterprise-updater/debians

# Need custom handling for prod ini file, since puppet mucks with it.
cp /opt/web/production.ini /tmp/production.ini
sudo dpkg -i aerofs-web.deb
cp /tmp/production.ini /opt/web/production.ini

sudo dpkg -i aerofs-sp.deb
sudo dpkg -i aerofs-bootstrap.deb
