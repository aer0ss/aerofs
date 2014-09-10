#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..
ENV="$HOME/bunker-env"

# Create a directory and touch a flag that's needed to serve bunker locally
# because bunker assumes the web server is running on the appliance
mkdir -p state
mkdir -p flags && touch flags/configuration-initialized-flag

# Create the version file if none exists
PACKAGES_DIR="$SRC_ROOT/../out.ant/packages"
VERSION_FILE="$PACKAGES_DIR/current.ver"

if [[ ! -f "$VERSION_FILE" ]]; then
  mkdir -p "$PACKAGES_DIR" && echo 'Version=0.0.1' > "$VERSION_FILE"
fi

# Create virtualenv
virtualenv "$ENV"

# Install bunker's dependencies in virtualenv
"$ENV/bin/pip" install --requirement ${SRC_ROOT}/bunker/requirements-exact.txt

# Install links for python-lib and bunker
"$ENV/bin/pip" install --editable "$SRC_ROOT/python-lib"
"$ENV/bin/pip" install --editable "$SRC_ROOT/bunker"
