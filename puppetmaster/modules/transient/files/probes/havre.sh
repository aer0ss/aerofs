#!/bin/bash -e
/opt/sanity/probes/tools/port.sh localhost 8083 "proxy port"
/opt/sanity/probes/tools/port.sh localhost 8084 "tunnel port"
