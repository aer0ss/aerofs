#!/bin/bash -ex

cd /opt/spdb

if [ "$(whoami)" != "root" ]
then
    echo "Must run as root."
    exit 1
fi

mysql mysql < init.sql
mysql aerofs_sp < sp.sql
