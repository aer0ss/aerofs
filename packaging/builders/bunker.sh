#!/bin/bash

set -e -u

NAME=bunker
SOURCE_DIR=../src/bunker
PYTHONLIB_DIR=../src/python-lib
OUTPUT_DIR=build/$NAME
OPT=$OUTPUT_DIR/opt/$NAME
DEBIAN=$OUTPUT_DIR/DEBIAN

# make the output directory
mkdir -p $OUTPUT_DIR

# make the debian script directory and copy to it
mkdir -p $DEBIAN
RESOURCES=$SOURCE_DIR/resources
for f in control postinst postrm
do
    cp -L $RESOURCES/$f $DEBIAN
done

# The postinst script logs to this folder, so ensure it exists
mkdir -p $OUTPUT_DIR/var/log/bunker

# make the directory in which bunker is installed in
# for all copies, cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
mkdir -p $OPT
cp -a -L $SOURCE_DIR/web $OPT/
# Also include stuff needed for package installation
cp -a -L $SOURCE_DIR/requirements.txt $OPT/
# Include the entry point script
cp -a -L $SOURCE_DIR/entry.py $OPT/

# make a folder for bunker to store CSRF tokens in; it'll be writable by www-data
mkdir -p $OPT/state

# Fetch other dependency source packages, if needed
# PIP_CACHE is a flat directory of .tar.gz source files from PyPI.
SDIST=$OPT/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/bunker
tools/pip-prefetch.sh "$SOURCE_DIR/requirements.txt" "$SDIST_CACHE"

# Copy PyPI dependency packages to /opt/bunker/sdist,
# so they'll be available at package install time.
mkdir -p $SDIST
cp $SDIST_CACHE/* $SDIST/

# include the actual python packages we need
tools/python-buildpackage.sh "$PYTHONLIB_DIR" "$SDIST" "$OPT/requirements.txt"
tools/python-buildpackage.sh "$SOURCE_DIR" "$SDIST" "$OPT/requirements.txt"
