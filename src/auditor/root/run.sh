#!/bin/bash
set -e

/container-scripts/import-ca-cert-to-java

/container-scripts/certify $(/container-scripts/get-config-property base.verkehr.host) /opt/auditor/auditor

# Set a high ulimit for no files to allow a huge # of users to connect
ulimit -S -n 1024000
ulimit -H -n 1024000

echo Starting up Auditor...
cd /opt/auditor
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/auditor -Xmx500m \
    -jar aerofs-auditor.jar auditor.properties
