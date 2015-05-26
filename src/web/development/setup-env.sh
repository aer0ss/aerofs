#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..
ENV="$HOME/.aerofs-web-env"

# Create virtualenv
virtualenv "$ENV"

# Install web's dependencies in virtualenv
"$ENV/bin/pip" install --requirement ${SRC_ROOT}/web/root/requirements-exact.txt

# Install links for python-lib and web
"$ENV/bin/pip" install --editable "$SRC_ROOT/python-lib"
"$ENV/bin/pip" install --editable "$SRC_ROOT/web"
