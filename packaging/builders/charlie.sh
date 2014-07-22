#!/bin/bash
set -e -u

OUTPUT_DIR=build/charlie
SDIST=$OUTPUT_DIR/opt/charlie/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/charlie
REQUIREMENTS_IN=charlie/opt/charlie/requirements-exact.txt
REQUIREMENTS_OUT=$OUTPUT_DIR/opt/charlie/requirements-exact.txt

tools/pip-prefetch.sh "$REQUIREMENTS_IN" "$SDIST_CACHE"

mkdir -p $SDIST
cp -a "$SDIST_CACHE"/* "$SDIST"/

# Include log folder
mkdir -p $OUTPUT_DIR/var/log/charlie

# Include aerofs-common, needed for config client
tools/python-buildpackage.sh "../src/python-lib" "$SDIST" "$REQUIREMENTS_OUT"
