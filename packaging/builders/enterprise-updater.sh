#!/bin/bash -e

OPT=enterprise-updater/opt/enterprise-updater
DEBIANS=$OPT/debians
INSTALLERS=$OPT/installers

mkdir -p $DEBIANS
mkdir -p $INSTALLERS

# Pull in debians.
rm -f $DEBIANS/*

# Copy over the debs that we require.
for required in \
    sp \
    web \
    bootstrap \
    installers
do
    cp debs/aerofs-${required}.deb $DEBIANS/
done

# Clean other debs. Unique to the updater deb!
rm -f debs/*

# Pull in latest installers.
../tools/enterprise/installers/pull-binaries.sh $INSTALLERS/
