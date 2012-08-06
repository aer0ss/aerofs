#!/bin/bash
#
# Script to create the vmhost.addr file required by the KVM's to communicate
# with this VM Host.

source $(dirname $0)/vmhost-common.sh

VMHOST_ADDR_FILE='/mnt/share/.config/vmhost.addr'

# Only create the vmhost.addr file if the bootstrap phase is done, i.e. if the
# .config directory has been created.
if [ -d $(dirname $VMHOST_ADDR_FILE) ]
then
    ip=$(vmhost_getip)

    # If the IP is not null, i.e. we have an IP.
    if [ ! -z $ip ]
    then
        echo $ip > $VMHOST_ADDR_FILE
    fi
fi
