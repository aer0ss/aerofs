#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..

virtualenv ~/env
~/env/bin/pip install --requirement ${SRC_ROOT}/web/requirements.txt
cd "$SRC_ROOT"/python-lib
~/env/bin/python setup.py develop
cd "$SRC_ROOT"/web
~/env/bin/python setup.py develop
