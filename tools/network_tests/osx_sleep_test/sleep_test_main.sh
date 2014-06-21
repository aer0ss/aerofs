#!/bin/bash
# This is the main script for the sleep tests and contains the bulk of the logic.
# We create a test dir in the awake actor and make the sleepy actor go to sleep.
# We add files under the test dir in the awake actor and wakeup the sleepy actor
# (using wake on lan) and make sure that the sleepy actor has the same number of files.
# If it doesn't, we send a notification email and exit.
sleepy_host=$1
awake_host=$2
file_prefix="test"
dir_name="sleep_test"
# 120 secs = 2 minutes. If something doesn't respond for 2 minutes, kill script send email.
timeout=120
offset=1

function pingTillAwakeOrAsleep()
{
    # This function takes in a string argument that specifies if we
    # are supposed to ping the sleep actor till it becomes awake or
    # or till it goes back to sleep.
    should_be_awake=$1
    start_time=$(date +%s)
    current_time=$(date +%s)
    while (( ($current_time - $start_time) < $timeout ))
    do
        if [ "$should_be_awake" = true ]
        then
            # If we want to proceed only after the actor comes awake
            # then keep pinging till we get a response.
            ping -c 2 $sleepy_host > /dev/null && break
        else
            # If we want to proceed only after the actor goes to sleep,
            # then keep pinging till we DON'T get a response.
            ! ping -c 2 $sleepy_host > /dev/null && break
        fi
        current_time=$(date +%s)
    done
    # If we have been pinging for 120 seconds but haven't broken out of the
    # loop, then exit the program. It probably means something has gone wrong.
    if (( ($current_time - $start_time) >= $timeout ))
    then
        exit 1
    fi
}

function sendUnsyncedEmail()
{
    email_body="Yo main man, file count mismatch. Expected file count: ${awake_actor_file_count}"
    echo $email_body | mail -s "Eureka Eureka" abhishek@aerofs.com
}

function Main()
{
    # Make sure that the OSX actor is awake to start with.
    pingTillAwakeOrAsleep true

    count=0
    ssh $awake_host "mkdir ~/AeroFS/${dir_name}; exit"

    while true
    do
        echo "Sleep OSX actor"
        ssh $sleepy_host 'pmset sleepnow; exit' > /dev/null

        count=$(( count+1 ))
        filename="${file_prefix}${count}"

        echo "Creating files in Never sleepy Actor"
        ssh $awake_host "touch ~/AeroFS/${dir_name}/${filename};exit"

        awake_actor_file_count=$(ssh $awake_host "find ~/AeroFS/sleep_test -name \"test*\" | wc -l;exit")

        echo "Asserting OSX actor is asleep before waking it up."
        pingTillAwakeOrAsleep false

        echo "Waking up OSX actor"
        wakeonlan -i 10.0.0.0 3c:07:54:56:6b:76 > /dev/null

        echo "Asserting OSX actor is awake before checking file count."
        pingTillAwakeOrAsleep true

        echo "Checking file count in OSX actor."
        response=$(ssh $sleepy_host "bash -s" < ./file_count_checker.sh $awake_actor_file_count)

        # If response from file count checker is Synced, that means file synced after
        # the actor woke up from sleep. So just continue. Else, send email to the coolest
        # guy in the office and exit.
        if [ "$response" = Synced ]
        then
            echo "File synced properly.# Files synced: ${awake_actor_file_count}"
        else
            echo "Count mismatch detected. Sending email."
            sendUnsyncedEmail
        fi
    done
}

set -e

Main "$@";
