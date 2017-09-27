#!/bin/bash
set -e

echo Starting up Sparta...
cd /opt/sparta
/container-scripts/restart-on-error java -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/sparta -jar aerofs-sparta.jar sparta.properties
