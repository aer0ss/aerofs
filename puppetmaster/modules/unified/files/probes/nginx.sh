#!/bin/bash -e
/opt/sanity/probes/tools/port.sh localhost 80 "redirect to 443"
/opt/sanity/probes/tools/port.sh localhost 443 "client website"
/opt/sanity/probes/tools/port.sh localhost 1029 "ca service"
