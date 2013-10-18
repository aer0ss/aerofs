#!/bin/bash
set -e -u

OPT=build/enterprise-updater/opt/enterprise-updater
DEBIANS=$OPT/debians
INSTALLERS=$OPT/installers

mkdir -p $DEBIANS
mkdir -p $INSTALLERS

# Copy over the debs that we require.
for required in \
    sp \
    web \
    bootstrap \
    repackaging
do
    cp debs/aerofs-${required}.deb $DEBIANS/
done

# Clean other debs. Unique to the updater deb!
rm -f debs/*

# Pull in latest installers.
../tools/enterprise/repackaging/pull-installers.sh $INSTALLERS/
