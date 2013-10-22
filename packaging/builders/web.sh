#!/bin/bash
set -e -u

SOURCE_DIR=../src/web
OUTPUT_DIR=build/web
mkdir -p $OUTPUT_DIR

OPT=$OUTPUT_DIR/opt/web
DEBIAN=$OUTPUT_DIR/DEBIAN

# files for dpkg
mkdir -p $DEBIAN
RESOURCES=$SOURCE_DIR/resources
for f in control postinst prerm conffiles
do
    cp $RESOURCES/$f $DEBIAN
done
# The postinst script logs to this folder, so ensure it exists
mkdir -p $OUTPUT_DIR/var/log/web

# Include the entirety of the src/python-lib repo, since we don't package it
# TODO: package python-lib as tarball and add to requirements.txt?
EXTRA=$OPT/extra
mkdir -p $EXTRA
cp -a ../src/python-lib $EXTRA

# Copy the actual web module to the output dir
# cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
mkdir -p $OPT
cp -a $SOURCE_DIR/web $OPT/
# Also include stuff needed for package installation
cp -a $SOURCE_DIR/{MANIFEST.in,README.txt,CHANGES.txt,setup.py,requirements.txt} $OPT/
# Include the wsgi application file that uwsgi will run
cp -a $SOURCE_DIR/production.wsgi $OPT/

# Fetch other dependency source packages, if needed
# PIP_CACHE is a flat directory of .tar.gz source files from PyPI.
SDIST=$OPT/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/web
tools/pip-prefetch.sh "../src/web/requirements.txt" "$SDIST_CACHE"

# Copy dependency packages to /opt/web/sdist, so they'll be available at
# package install time.
mkdir -p $SDIST
cp $SDIST_CACHE/* $SDIST/
