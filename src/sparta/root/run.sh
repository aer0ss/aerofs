#!/bin/bash
set -e

/container-scripts/create-database aerofs_sp

echo Starting up Sparta...
cd /opt/sparta
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/sparta \
    -jar aerofs-sparta.jar sparta.properties
