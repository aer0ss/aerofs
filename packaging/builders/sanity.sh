#!/bin/bash
set -e -u

OUTPUT_DIR=build/sanity
mkdir -p $OUTPUT_DIR

# Fetch dependencies
REQUIREMENTS=$OUTPUT_DIR/opt/sanity/requirements-exact.txt
SDIST_CACHE=$HOME/.aerofs-cache/pip/sanity
tools/pip-prefetch.sh "$REQUIREMENTS" "$SDIST_CACHE"

# Add deps to package
SDIST=$OUTPUT_DIR/opt/sanity/sdist
mkdir -p $SDIST
cp -a "$SDIST_CACHE"/* "$SDIST"/
