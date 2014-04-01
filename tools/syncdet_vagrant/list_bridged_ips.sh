#!/usr/bin/env bash

function get_ip()
{
    local GET_IP_CMD="ip addr show dev eth2 2>/dev/null \
        | grep 'inet ' \
        | sed -E 's/.*inet ((\.?[0-9]+){4}).*/\1/g' \
        | grep . || echo There is no bridged adapter on this device"
    echo $(vagrant ssh $1 -c "$GET_IP_CMD")
}

# Get list of vms.
vms=$(vagrant status \
    | grep 'running' \
    | awk '{print $1}')

# Retrieve IPs from vms and print.
for vm in $vms; do
    echo $vm": "$(get_ip $vm)
done
