#!/bin/bash
set -e -u

OUTPUT_DIR=build/repackaging
OPT=$OUTPUT_DIR/opt/repackaging

# Fetch python dependency packages to cache
REQUIREMENTS=repackaging/opt/repackaging/requirements-exact.txt
SDIST_CACHE=$HOME/.aerofs-cache/pip/repackaging
tools/pip-prefetch.sh $REQUIREMENTS $SDIST_CACHE

# Add dependency packages to package
SDIST=$OPT/sdist
mkdir -p $SDIST
cp -a $SDIST_CACHE/* $SDIST/
