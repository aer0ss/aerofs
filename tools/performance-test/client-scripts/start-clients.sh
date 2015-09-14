#!/bin/bash
set -eu

function usage()
{
    echo "Usage: $0 <approot location>"
}

if [ $# -ne 1 ]
then
    usage
    exit 1
fi

if [ -z "$HOME" ]
then
    echo "Need to be able to find HOME directory as \$HOME"
    exit 1
fi

APPROOT="$1"
RTROOT="$HOME"/rtroot

for i in $( ls -d "$RTROOT"/user*/device* )
do
    echo "starting client at $i"
    nohup "$APPROOT"/run $i cli &
    # wait 30 seconds so that too many clients are not started too quickly
    # TODO: waiting after the last client is started may be un-necessary
    sleep 30
done
