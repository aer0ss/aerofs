#!/bin/bash
#
# Script used to generate a random ID for a given VM host. Creates the vmhost.id
# file which contains the random ID. Only creates the file once. The ID is used
# to uniquely identify the VM Host on the secure protobuf channel (not for
# security, just to identify uniqueness).

VMHOST_ID_FILE='/mnt/share/.config/vmhost.id'

# If the file doesn't exist, and the bootstrap step has created the .config
# directory...
if [ ! -f $VMHOST_ID_FILE ] && [ -d $(dirname $VMHOST_ID_FILE) ]
then
    echo $RANDOM | md5sum | awk -F' ' '{print $1}' > $VMHOST_ID_FILE
fi
