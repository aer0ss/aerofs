#!/bin/bash
set -e

/container-scripts/create-database trifrost
MYSQL_PASSWD="$(/container-scripts/get-config-property mysql.password)"
MYSQL_USER="$(/container-scripts/get-config-property mysql.user)"
MYSQL_URL="$(/container-scripts/get-config-property mysql.url)"

echo Starting up Trifrost...
cd /opt/trifrost
sed -e "s/MYSQL_PASSWORD/$MYSQL_PASSWD/g" \
    -e "s/MYSQL_USER/$MYSQL_USER/g" \
    -e "s/MYSQL_URL/$MYSQL_URL/g" \
    trifrost.yml.tmplt > trifrost.yml
/container-scripts/restart-on-error java -Xmx1024m -jar aerofs-trifrost.jar trifrost.yml
