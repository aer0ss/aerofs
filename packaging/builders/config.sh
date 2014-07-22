#!/bin/bash
set -e -u

OUTPUT_DIR=build/config
SDIST=$OUTPUT_DIR/opt/config/sdist
SDIST_CACHE=$HOME/.aerofs-cache/pip/config
REQUIREMENTS_IN=config/opt/config/requirements-exact.txt
REQUIREMENTS_OUT=$OUTPUT_DIR/opt/config/requirements-exact.txt

tools/pip-prefetch.sh "$REQUIREMENTS_IN" "$SDIST_CACHE"

mkdir -p $SDIST
cp -a "$SDIST_CACHE"/* "$SDIST"/

# Also include the aerofs-licensing Python package
LICENSING_DIR="../src/licensing"
tools/python-buildpackage.sh "$LICENSING_DIR" "$SDIST" "$REQUIREMENTS_OUT"
