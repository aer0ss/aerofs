#!/bin/bash
set -e

/container-scripts/create-database bifrost

# HACK WARNING: This to work around the circular dependency between SP and Bifrost.
# TODO (WW) remove SP => Bifrost dependency. SP calls Bifrost only for URL sharing.
# However these calls can and should be made by the clients.
#
echo "Advertising service IP..."
/container-scripts/set-config-property hack_bifrost_service_ip $(hostname -i)

echo Starting up Bifrost...
cd /opt/bifrost
java -XX:+HeapDumpOnOutOfMemoryError -jar aerofs-bifrost.jar bifrost.properties
