#!/bin/bash -e
/opt/sanity/probes/tools/port.sh localhost 5435 "client config service"
/opt/sanity/probes/tools/port.sh localhost 5436 "server config service"
/opt/sanity/probes/tools/port.sh localhost 1029 "ca service"
