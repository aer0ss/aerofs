#!/bin/bash
set -e

echo Starting up Polaris...
cd /opt/polaris
/container-scripts/restart-on-error java -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/polaris -Xmx1024m -jar aero-polaris.jar polaris.yml
