#!/bin/bash
set -e

/container-scripts/create-database aerofs_ca
MYSQL_PASSWD="$(/container-scripts/get-config-property mysql.password)"
MYSQL_USER="$(/container-scripts/get-config-property mysql.user)"
MYSQL_URL="$(/container-scripts/get-config-property mysql.url)"

echo Starting up Certificate Authority...
cd /opt/ca-server
sed -e "s/MYSQL_PASSWORD/$MYSQL_PASSWD/g" \
    -e "s/MYSQL_USER/$MYSQL_USER/g" \
    -e "s/MYSQL_URL/$MYSQL_URL/g" \
    ca.yml.tmplt > ca.yml
java -XX:+HeapDumpOnOutOfMemoryError -jar aerofs-ca-server.jar ca.yml
