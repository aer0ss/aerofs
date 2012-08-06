#!/bin/bash

# Get the IP Address of this box.
# Echo:
#   The IP address if the network is up, empty string otherwise.
vmhost_getip()
{
    ifconfig br0 \
        | grep "inet addr" \
        | grep -v inet6 \
        | awk -F' ' '{print $2}' \
        | awk -F: '{print $2}'
}
