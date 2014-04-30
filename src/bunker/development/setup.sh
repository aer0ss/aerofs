#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..
ENV="$HOME/bunker-env"

# Create a directory and touch a flag that's needed to serve bunker locally
# because bunker assumes the web server is running on the appliance
mkdir state
mkdir flags && touch flags/configuration-initialized-flag

# Create the version file if none exists
VERSION_FILE="$SRC_ROOT/../out.ant/packages/current.ver"
[[ -f $VERSION_FILE ]] || echo 'Version=1.2.3' > $VERSION_FILE

# Create virtualenv
virtualenv "$ENV"

# Install bunker's dependencies in virtualenv
"$ENV/bin/pip" install --requirement ${SRC_ROOT}/bunker/requirements.txt

# Install links for python-lib and bunker
"$ENV/bin/pip" install --editable "$SRC_ROOT/python-lib"
"$ENV/bin/pip" install --editable "$SRC_ROOT/bunker"
