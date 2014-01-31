#!/bin/bash
set -e -u

SOURCE_DIR=../src/lizard
OUTPUT_DIR=build/lizard
mkdir -p $OUTPUT_DIR

# Postinst and the app log here, so ensure it exists
mkdir -p $OUTPUT_DIR/var/log/lizard

OPT=$OUTPUT_DIR/opt/lizard
mkdir -p $OPT
# A folder for lizard to store some state in; it'll be writable by www-data
mkdir -p $OPT/state
# Include requirements for package installation
cp -a $SOURCE_DIR/requirements.txt $OPT/
# Include base config.  This contains configuration that is used in both dev
# and production.  Deployment-specific configuration comes from
# additional_config.py which is provided by puppet for production use and the
# repo for development use.
cp -a $SOURCE_DIR/config.py $OPT/
# Include entry point script
cp -a $SOURCE_DIR/entry.py $OPT/
# Include static assets.  Since we share assets with the web module, we must
# use the -L flag to cp to dereference symlinks.
mkdir -p $OPT/static
cp -pRL $SOURCE_DIR/lizard/static/* $OPT/static

SDIST=$OPT/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/lizard
tools/pip-prefetch.sh "../src/lizard/requirements.txt" "$SDIST_CACHE"

# Copy PyPI dependencies to /opt/lizard/sdist
mkdir -p $SDIST
cp $SDIST_CACHE/* $SDIST/

# Include aerofs-licensing, upon which lizard depends
tools/python-buildpackage.sh "../src/licensing" "$SDIST" "$OPT/requirements.txt"
# Include the aerofs-lizard package
tools/python-buildpackage.sh "../src/lizard" "$SDIST" "$OPT/requirements.txt"
