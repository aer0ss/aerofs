#!/bin/bash
# This script checks if the AeroFS in the sleepy actor has the same number of
# files as the AeroFS in the awake actor. We set a timeout of 60 secs to check
# for the equality of the # of files, since AeroFS might not start immediately,
# or all the files might not sync immediately. If the files have not synced within
# 60 secs, we send back an Unsynced message.
expected_file_count=$1
timeout_secs=60

start_time=$(date +%s)
current_time=$(date +%s)
# For 60 secs, keep checking if the files on both the actors are the same
# If they are not the same after 60 secs, break out of the loop and send back
# unsynced message.
while (( ($current_time - $start_time) < $timeout_secs ))
do
    receiver_file_count=$(find ~/AeroFS/sleep_test -name "test*" | wc -l)
    if (( $receiver_file_count == $expected_file_count ))
    then
        # If the files sync, just exit.
        echo Synced
        exit
    else
        continue
    fi
done
echo Unsynced
