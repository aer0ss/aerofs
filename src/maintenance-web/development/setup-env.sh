#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..
ENV="$HOME/.aerofs-maintenance-web-env"

virtualenv "$ENV"
"$ENV/bin/pip" install --requirement ${SRC_ROOT}/maintenance-web/root/requirements-exact.txt
"$ENV/bin/pip" install --editable "$SRC_ROOT/maintenance-web"
