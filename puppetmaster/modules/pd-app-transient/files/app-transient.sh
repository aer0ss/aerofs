#!/bin/bash -e

PORT_SCRIPT=/opt/sanity/probes/tools/port.sh

$PORT_SCRIPT localhost 443 "Webadmin and SP"
$PORT_SCRIPT localhost 3478 "STUN server"
$PORT_SCRIPT localhost 5222 "Ejabberd"
$PORT_SCRIPT localhost 8888 "Zephyr relay"
$PORT_SCRIPT localhost 29438 "Verkehr subscribe"

echo "SUCCESS!"
