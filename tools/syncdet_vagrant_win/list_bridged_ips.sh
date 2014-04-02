#!/usr/bin/env bash

function get_ip()
{
    local GET_IP_CMD="netsh interface ip show config name=\"Local Area Connection 3\" \
        | grep 'IP Address' \
        | sed -E 's/.*IP Address[^0-9]*((\.?[0-9]{1,3}){4}).*/\1/g' \
        | grep . || echo There is no bridged adapter on this device"
    echo $(ssh aerofstest@$1 "$GET_IP_CMD")
}

# Get number of vms.
declare -i vms=$(vagrant status | grep 'running (virtualbox)' | wc -l)
declare -i top_ip=$vms+109

# Retrieve IPs from vms and print.
for last in $(seq 110 $top_ip); do
    echo $(get_ip 192.168.50.$last)
done
