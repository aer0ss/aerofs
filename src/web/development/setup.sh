#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..

echo ">>>"
echo ">>> N.B. ROOT ACCESS IS REQUIRED. YOU MIGHT BE PROMPTED FOR YOU PASSWORD."
echo ">>>"

virtualenv ~/env
cd "$SRC_ROOT"/python-lib
~/env/bin/python setup.py develop
cd "$SRC_ROOT"/web
~/env/bin/python setup.py develop
