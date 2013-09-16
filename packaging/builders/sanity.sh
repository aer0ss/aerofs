#!/bin/bash
set -e -u

REQUIREMENTS=sanity/opt/sanity/requirements.txt
SDIST=sanity/opt/sanity/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/sanity

rm -rf $SDIST
tools/pip-prefetch.sh "$REQUIREMENTS" "$SDIST_CACHE"

mkdir -p $SDIST
cp -a "$SDIST_CACHE"/* "$SDIST"/
