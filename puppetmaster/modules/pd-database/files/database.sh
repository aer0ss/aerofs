#!/bin/bash -e

PORT_SCRIPT=/opt/sanity/probes/tools/port.sh

$PORT_SCRIPT localhost 3306 "MySQL"
$PORT_SCRIPT localhost 6379 "Redis"

echo "SUCCESS!"
