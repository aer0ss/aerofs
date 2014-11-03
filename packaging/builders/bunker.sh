#!/bin/bash

set -e -u

# FIXME: fragile path behavior. This needs to be one level
# down from repo root, and one level up from build, and
# also tools. Fix this when less stressed.

HERE=$PWD
NAME=bunker
PYTHONLIB_DIR=$HERE/../src/python-lib
OUTPUT_DIR=$HERE/build/$NAME
OPT=$OUTPUT_DIR/opt/$NAME
DEBIAN=$OUTPUT_DIR/DEBIAN

# First, make sure all of the web assets that we'll be using exist
pushd $HERE/../src/web/web && make clean && make && popd

# FIXME (AG): create a temporary source dir with a copy
# of all the bunker files to avoid any symlink issues

ORIGINAL_SOURCE_DIR=$HERE/../src/bunker
SOURCE_DIR=$(mktemp -d -t bunker.XXXX)
pushd $ORIGINAL_SOURCE_DIR
tar cvhf - * | $(cd $SOURCE_DIR; tar xf -)
popd

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
# we use tar to preserve ownership and permissions consistently
# on both Linux and OSX
mkdir -p $OPT
pushd $SOURCE_DIR
tar chf - web requirements-exact.txt entry.py | $(cd $OPT; tar xf -)
popd

# make a folder for bunker to store CSRF tokens in; it'll be writable by www-data
mkdir -p $OPT/state

# Fetch other dependency source packages, if needed
# PIP_CACHE is a flat directory of .tar.gz source files from PyPI.
SDIST=$OPT/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/bunker
tools/pip-prefetch.sh "$SOURCE_DIR/requirements-exact.txt" "$SDIST_CACHE"

# Copy PyPI dependency packages to /opt/bunker/sdist,
# so they'll be available at package install time.
mkdir -p $SDIST
cp $SDIST_CACHE/* $SDIST/

# include the actual python packages we need
tools/python-buildpackage.sh "$PYTHONLIB_DIR" "$SDIST" "$OPT/requirements-exact.txt"
tools/python-buildpackage.sh "$SOURCE_DIR" "$SDIST" "$OPT/requirements-exact.txt"
