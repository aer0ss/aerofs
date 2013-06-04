#!/bin/bash -e

PORT_SCRIPT=/opt/sanity/probes/tools/port.sh

$PORT_SCRIPT localhost 443 "Client config service"
$PORT_SCRIPT localhost 1029 "Certificate authority"
$PORT_SCRIPT localhost 8080 "Server config service"

echo "SUCCESS!"
