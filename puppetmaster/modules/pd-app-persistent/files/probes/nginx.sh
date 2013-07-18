#!/bin/bash -e
/opt/sanity/probes/tools/port.sh localhost 443 "client config service"
/opt/sanity/probes/tools/port.sh localhost 8080 "server config service"
/opt/sanity/probes/tools/port.sh localhost 1029 "ca service"
