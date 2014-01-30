#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..
ENV="$HOME/bunker-env"

# Create virtualenv
virtualenv "$ENV"

# Install bunker's dependencies in virtualenv
"$ENV/bin/pip" install --requirement ${SRC_ROOT}/bunker/requirements.txt

# Install links for python-lib and bunker
"$ENV/bin/pip" install --editable "$SRC_ROOT/python-lib"
"$ENV/bin/pip" install --editable "$SRC_ROOT/bunker"
