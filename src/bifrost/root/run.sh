#!/bin/bash
set -e

/container-scripts/create-database bifrost

echo Starting up Bifrost...
cd /opt/bifrost
java -XX:+HeapDumpOnOutOfMemoryError -jar aerofs-bifrost.jar bifrost.properties
