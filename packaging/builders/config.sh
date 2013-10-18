#!/bin/bash
set -e -u

OUTPUT_DIR=build/config
SDIST=$OUTPUT_DIR/opt/config/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/config
REQUIREMENTS=config/opt/config/requirements.txt

tools/pip-prefetch.sh "$REQUIREMENTS" "$SDIST_CACHE"

mkdir -p $SDIST
cp -a "$SDIST_CACHE"/* "$SDIST"/
