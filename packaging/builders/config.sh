#!/bin/bash
set -e -u

REQUIREMENTS=config/opt/config/requirements.txt
SDIST=config/opt/config/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/config

rm -rf $SDIST
tools/pip-prefetch.sh "$REQUIREMENTS" "$SDIST_CACHE"

mkdir -p $SDIST
cp -a "$SDIST_CACHE"/* "$SDIST"/
