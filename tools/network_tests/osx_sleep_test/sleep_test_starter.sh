#!/bin/bash
# This script calls the main script which contains the bulk of the sleep
# tests logic. If the main script sends an error code, we send a notification
# email.
Usage()
{
    echo "Usage: $0 sleepy_actor_ip_addr awake_actor_ip_addr"
}

if [ $# != 2 ]
then
    Usage
    exit
fi

bash -e ./sleep_test_main.sh $1 $2 | tee osx_sleep_test.log

if (( ${PIPESTATUS[0]} == 1))
then
    echo "Run upstairs, something bad has happened" | mail -s "FAIL" abhishek@aerofs.com
fi
