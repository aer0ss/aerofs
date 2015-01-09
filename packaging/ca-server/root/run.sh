#!/bin/bash
set -e
#
# This script is adapted from src/boostrap/resources/scripts/ca-deploy.sh
#

newpass()
{
    echo $RANDOM | sha1sum | awk -F' ' '{print $1}'
}

cd /opt/ca

if [ ! -f prod/cacert.pem ]
then
    echo cacert.pem doesn\'t exist. Initialize CA.
    ./deploy.sh $(newpass)
fi

# -u to disable log output buffering
python -u ca.py
