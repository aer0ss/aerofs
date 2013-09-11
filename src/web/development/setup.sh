#!/bin/bash
set -e
cd $(dirname "${BASH_SOURCE[0]}")
DEV_ROOT=$(pwd)
SRC_ROOT="$DEV_ROOT"/../..

function setup_configuration()
{
    local host=$1

    sudo mkdir -p /etc/aerofs/
    sudo echo "https://$host:5436/" > /etc/aerofs/configuration.url
    sudo mkdir -p /etc/ssl/certs
    sudo wget "http://$host:1029/prod/cacert.pem" --output-document=/etc/ssl/certs/AeroFS_CA.pem
}

if [ $# -ne 1 ]
then
    echo "Usage: $0 <mode>"
    echo
    echo "Available modes: prod, modular, unified."
    echo
    echo "Notes:"
    echo " - If running in unified or modular mode, the corresponding development"
    echo "   system must be running."
    echo " - You might be prompted for your Mac OS X system password (so we can"
    echo "   create /etc/aerofs/configuration.url, install things, etc.)."

    exit 1
fi

MODE=$1

echo
echo ">>> N.B. ROOT ACCESS IS REQUIRED. YOU MIGHT BE PROMPTED FOR YOU PASSWORD."
echo

virtualenv ~/env
cd "$SRC_ROOT"/python-lib
~/env/bin/python setup.py develop
cd "$SRC_ROOT"/web
~/env/bin/python setup.py develop

case "$MODE" in
    "unified")
        setup_configuration unified.syncfs.com
        ;;
    "modular")
        setup_configuration persistent.syncfs.com
        ;;
esac
