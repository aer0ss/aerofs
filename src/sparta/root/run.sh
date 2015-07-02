#!/bin/bash
set -e

/container-scripts/create-database aerofs_sp
# Due to having bifrost and SP split in the past, sparta needs to be able to
# merge the bifrost and aerofs_sp databases, and the sanest way to do that is
# to ensure both are available.
/container-scripts/create-database bifrost

echo Starting up Sparta...
cd /opt/sparta
/container-scripts/restart-on-error java -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/sparta -jar aerofs-sparta.jar sparta.properties
