#!/bin/bash -e

ip=$(ifconfig | \
    grep inet | \
    grep -v inet6 | \
    grep -v 127.0.0.1 | \
    tail -1 | \
    awk -F' ' '{print $2}' | \
    awk -F: '{print $2}')

/opt/sanity/probes/tools/port.sh $ip 3478
