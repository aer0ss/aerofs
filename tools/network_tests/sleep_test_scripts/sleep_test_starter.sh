#!/bin/bash
# This script calls the main script which contains the bulk of the sleep
# tests logic. If the main script sends an error code, we send a notification
# email.
Usage()
{
    echo "Usage: $0 awake_actor_ip_addr sleepy_actor_ip_addr sleepy_actor_mac_address sleepy_actor_machine_type(OSX|WIN)"
}

die()
{
    echo >&2 "$@"
    exit 1
}

if [ $# != 4 ]
then
    Usage
    exit
fi
# Validate mac address
echo $3 | egrep "^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$" || die "Provide a valid mac address"
echo $4 | grep -w -e WIN -e OSX || die "The machine type must be either WIN or OSX"

bash -e ./sleep_test_main.sh $1 $2 $3 $4 | tee ${4}_sleep_test.log

if (( ${PIPESTATUS[0]} == 1))
then
    echo "Run upstairs, something bad has happened" | mail -s "FAIL" abhishek@aerofs.com
fi
