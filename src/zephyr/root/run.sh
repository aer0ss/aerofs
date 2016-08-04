#!/bin/bash
set -e

ulimit -S -n 1024000
ulimit -H -n 1024000

echo Starting up Zephyr...
# Need cwd for the banner to work
cd /opt/zephyr/
/container-scripts/restart-on-error java -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/zephyr -d64 -Xms712m -Xmx1536m \
    -jar aero-zephyr.jar 0.0.0.0 8888
