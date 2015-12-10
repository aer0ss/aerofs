#!/bin/bash
set -e

/container-scripts/create-database polaris
MYSQL_PASSWD="$(/container-scripts/get-config-property mysql.password)"
MYSQL_USER="$(/container-scripts/get-config-property mysql.user)"
MYSQL_URL="$(/container-scripts/get-config-property mysql.url)"
LOG_LEVEL="$(/container-scripts/get-config-property base.log.level)"

echo Starting up Polaris...
cd /opt/polaris
sed -e "s/MYSQL_PASSWORD/$MYSQL_PASSWD/g" \
    -e "s/MYSQL_USER/$MYSQL_USER/g" \
    -e "s/MYSQL_URL/$MYSQL_URL/g" \
    -e "s/LOG_LEVEL/$LOG_LEVEL/g" \
    polaris.yml.tmplt > polaris.yml
/container-scripts/restart-on-error java -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/polaris -Xmx1024m -jar aerofs-polaris.jar polaris.yml
