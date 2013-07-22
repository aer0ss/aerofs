#!/bin/bash -e
/opt/sanity/probes/tools/port.sh localhost 9293 "publish port"
/opt/sanity/probes/tools/port.sh localhost 25234 "admin port"
/opt/sanity/probes/tools/port.sh localhost 29438 "subscribe port"
