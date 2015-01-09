#!/bin/bash
set -e

/container-scripts/create-database polaris

echo Starting up Polaris...
cd /opt/polaris
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/polaris -Xmx1536m \
    -jar aerofs-polaris.jar polaris.yml
