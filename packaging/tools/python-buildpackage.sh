#!/bin/bash
set -eu

# Builds an sdist package of the source in folder $1,
# places the newly-built package in folder $2, and appends
# an entry to the package to requirements.txt file $3.

PACKAGE_DIR="$1"
SDIST="$2"
REQUIREMENTS_TXT="$3"

# Build sdist package
pushd "$PACKAGE_DIR"
rm -rf dist
PACKAGE_NAME=$(python3 setup.py --name)
PACKAGE_VER=$(python3 setup.py --version)
python3 setup.py sdist --formats=gztar
popd

# Place sdist package in specified output folder
mv "$PACKAGE_DIR"/dist/* "$SDIST/"
rmdir "$PACKAGE_DIR/dist"

# Append to requirements.txt
echo "$PACKAGE_NAME==$PACKAGE_VER" >> "$REQUIREMENTS_TXT"
