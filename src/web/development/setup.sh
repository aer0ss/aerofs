#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..

# Create virtualenv
virtualenv $HOME/env

# Install web's dependencies in virtualenv
$HOME/env/bin/pip install --requirement ${SRC_ROOT}/web/requirements.txt
EASY_INSTALL_PTH=$HOME/env/lib/python2.7/site-packages/easy-install.pth

add_for_dev() {
	# $1 is path to folder
	# $2 is .egg-link path
	echo "$1" > "$2"
	echo "." >> "$2"
	grep "$1" "$EASY_INSTALL_PTH" > /dev/null || echo "$1" >> "$EASY_INSTALL_PTH"
}

# make the development copy of python-lib available within the virtualenv
add_for_dev "$SRC_ROOT/python-lib" $HOME/env/lib/python2.7/site-packages/aerofs-py-lib.egg-link

# make the development copy of web available within the virtualenv
add_for_dev "$SRC_ROOT/web" $HOME/env/lib/python2.7/site-packages/web.egg-link
