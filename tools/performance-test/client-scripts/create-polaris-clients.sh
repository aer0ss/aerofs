#!/bin/bash
set -eu

function usage()
{
    echo "Usage: $0 <users> <devices> <approot location> [polaris=share.syncfs.com]"
    echo "Warning: will destroy conflicting folders within ~/rtroot"
}

if [ $# -ne 3 ] && [ $# -ne 4 ]
then
    usage
    exit 1
fi

if [ -z "$HOME" ]
then
    echo "Need to be able to find HOME directory as \$HOME"
    exit 1
fi

( [[ "$1" =~ ^-?[0-9]+$ ]] && [[ "$2" =~ ^-?[0-9]+$ ]] ) || ( echo "Number of users and number of devices must both be integers"; exit 1 )

PWD="$( cd $(dirname $0) ; pwd -P )"
USERS=$(($1-1))
DEVICES=$(($2-1))
APPROOT="$3"
POLARIS="${4:-share.syncfs.com}"
RTROOT="$HOME"/rtroot

mkdir -p $RTROOT
rm -rf $RTROOT/user*
for i in $(seq 0 1 "$USERS")
do
    EMAIL=user-${RANDOM}-${RANDOM}-${RANDOM}-${RANDOM}-${RANDOM}@example.com
    $PWD/signup.sh -u "$EMAIL" -p "temp123" -w "$POLARIS"
    for j in $(seq 0 1 "$DEVICES")
    do
        USERRTROOT=$RTROOT/user$i/device$j
        mkdir -p $USERRTROOT

        cat > $USERRTROOT/unattended-setup.properties <<EOF
userid=$EMAIL
password=temp123
root=$USERRTROOT/rootanchor
EOF
        touch $USERRTROOT/polaris
        touch $USERRTROOT/lol
    done
done
