#!/bin/bash -e

cd /opt/spdb

if [ "$(whoami)" != "root" ]
then
    echo "Must run as root."
    exit 1
fi

if [ $# -ne 3 ]
then
    echo "Usage: <sp_schema> <sp_username> <sp_password>"
    exit 1
fi

sp_schema=$1
sp_username=$2
sp_password=$3

cp init.sql init-sed.sql

# Passing params to a sql script is super annoying. Just use sed instead.
sed -i "s/___sp_schema___/$sp_schema/g" init-sed.sql
sed -i "s/___sp_username___/$sp_username/g" init-sed.sql
sed -i "s/___sp_password___/$sp_password/g" init-sed.sql

mysql mysql < init-sed.sql
rm init-sed.sql

mysql $sp_schema < sp.sql
