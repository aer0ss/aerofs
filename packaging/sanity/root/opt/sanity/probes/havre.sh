#!/bin/bash -e
/opt/sanity/probes/tools/port.sh havre.service 8083 "proxy port"
/opt/sanity/probes/tools/port.sh havre.service 8084 "tunnel port"
