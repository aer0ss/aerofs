#!/bin/bash
set -e -u

cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..
ENV="$HOME/bunker-env"

# prompt for sudo privilege
echo "This script requires sudo privileges, please enter your password"
sudo echo >> /dev/null

# Create a directory and touch a flag that's needed to serve bunker locally
# because bunker assumes the web server is running on the appliance
sudo mkdir -p /opt/bunker/state && sudo chmod 777 /opt/bunker/state
sudo mkdir -p /var/aerofs && sudo touch /var/aerofs/configuration-initialized-flag

# Create virtualenv
virtualenv "$ENV"

# Install bunker's dependencies in virtualenv
"$ENV/bin/pip" install --requirement ${SRC_ROOT}/bunker/requirements.txt

# Install links for python-lib and bunker
"$ENV/bin/pip" install --editable "$SRC_ROOT/python-lib"
"$ENV/bin/pip" install --editable "$SRC_ROOT/bunker"
