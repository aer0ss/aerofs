#!/bin/bash
set -e

/container-scripts/create-database polaris
MYSQL_PASSWD="$(/container-scripts/get-config-property mysql.password)"
MYSQL_USER="$(/container-scripts/get-config-property mysql.user)"
MYSQL_URL="$(/container-scripts/get-config-property mysql.url)"

echo Starting up Polaris...
cd /opt/polaris
sed -e "s/MYSQL_PASSWORD/$MYSQL_PASSWD/g" \
    -e "s/MYSQL_USER/$MYSQL_USER/g" \
    -e "s/MYSQL_URL/$MYSQL_URL/g" \
    polaris.yml.tmplt > polaris.yml
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/polaris -Xmx1536m \
    -Dio.netty.allocator.numHeapArenas=0 -Dio.netty.allocator.numDirectArenas=0  \
    -Dio.netty.allocator.tinyCacheSize=0 -Dio.netty.allocator.smallCacheSize=0   \
    -Dio.netty.allocator.normalCacheSize=0 -Dio.netty.allocator.maxOrder=7       \
    -jar aerofs-polaris.jar polaris.yml
