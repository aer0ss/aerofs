#!/bin/bash -e

IFS='
'

usage()
{
    echo "AeroFS software update script."
    echo "Usage: $(basename $0) [-a=s]"
    echo "  -s  Update system packages as well."
    echo "  -n  Run without apt-get update."
}

SYSTEM=0
UPDATE=1

while getopts "anh" OPTION
do
     case $OPTION in
         s)
             SYSTEM=1
             ;;
         n)
             UPDATE=0
             ;;
         h)
            usage
            exit 0
            ;;
         ?)
            usage
            exit 1
            ;;
     esac
done

if [ $UPDATE -eq 1 ]
then
    sudo apt-get update
fi

if [ $SYSTEM -eq 1 ]
then
    sudo apt-get upgrade -y
else
    aerofs_packages=

    for line in $(dpkg --list | grep aerofs)
    do
        name=$(echo $line | awk -F' ' '{print $2}')
        aerofs_packages="$aerofs_packages $name"
    done

    eval sudo apt-get install -y $aerofs_packages
fi
