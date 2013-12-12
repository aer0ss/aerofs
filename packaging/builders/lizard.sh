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
# Include base config (this should probably be provided by puppet?)
cp -a $SOURCE_DIR/config.py $OPT/
# Include entry point script
cp -a $SOURCE_DIR/entry.py $OPT/

SDIST=$OPT/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/lizard
tools/pip-prefetch.sh "../src/lizard/requirements.txt" "$SDIST_CACHE"

# Copy PyPI dependencies to /opt/lizard/sdist
mkdir -p $SDIST
cp $SDIST_CACHE/* $SDIST/

# Also include the aerofs-lizard package
tools/python-buildpackage.sh "../src/lizard" "$SDIST" "$OPT/requirements.txt"
