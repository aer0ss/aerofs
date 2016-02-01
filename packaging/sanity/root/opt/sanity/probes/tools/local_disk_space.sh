#!/bin/bash

if [ $# -ne 2 ]
then
    echo "Usage: $0 <path> <required_space_gigabytes>"
    exit 1
fi

# save off args
mount_point=$1
required_space=$2

# This will round down to the nearest GB (1028 MB) block
# available.
disk_space=$(df -B 1G $path | awk 'NR == 2 {print $4}')

# Check to see if df could get details about path
if [ -z $disk_space ]
then
    echo "Unable to determine available disk space."
    exit 1
fi

# check to see if disk space is sufficient
if [ $disk_space -lt $required_space ]
then
    echo "Free space: ${disk_space}GB"
    exit 1
fi
