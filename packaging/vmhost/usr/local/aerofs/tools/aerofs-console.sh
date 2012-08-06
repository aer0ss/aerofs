#!/bin/bash
# This is the AeroFS user console that appears when you attach to the console of
# the VM Host.

source $(dirname $0)/vmhost-common.sh

SHARE=/mnt/share
PACKAGE=$SHARE/aerofs.aer

# Signal handler.
on_die()
{
    # Do nothing.
    echo >/dev/null
}

trap 'on_die' TERM SIGINT SIGTERM

# Trim a bash string.
# Params:
#   $1 = the string to trim.
# Echo:
#   The trimmed string.
trim()
{
    echo $1
}


# Get the current web URL.
# Echo:
#   The current web URL if the network is up, empty string otherwise.
geturl()
{
    ip=$(vmhost_getip)

    if [ "$ip" != "" ]
    then
        port=$(cat /usr/local/aerofs/python/bootstrap/config.ini \
            | grep port \
            | awk -F' ' '{print $3}')
        echo "http://$ip:$port/"
    fi
}

# Print the motd message and the web URL if the network is up.
# Echo:
#   motd message and web URL if the network is up, and an error message
#   otherwise.
motd()
{
    cat /etc/motd | grep -vi aerofs
}

# Want to print this as soon as possible so the screen doesn't jump around so
# much.
clear
motd
echo -e "Welcome to the AeroFS user console!"
echo -e "Please navigate to the URL provided below to configure your system.\n"
echo -e "$(geturl)\n"

# Display the user help message.
# Echo:
#   User help message.
help()
{
    echo -e "\nAvailable commands:"
    echo
    echo -e "  clear:   clear the console."
    echo -e "  url:     display current web URL."
    echo -e "  help:    display this help message.\n"
}

# Command prompt loop.
while [ 0 ]
do
    # AeroFS command prompt.
    echo -n "aerofs> "
    read command
    command=$(trim $command)

    if [ "$command" != "" ]
    then
        case $command in
            clear)
                clear
                motd
                ;;
            url)
                echo -e "\n$(geturl)\n"
                ;;
            help|?)
                help
                ;;
            # TODO disable later... hidden console command for AeroFS admins.
            inception)
                exit 0
                ;;
            *)
                echo -e "\nUnknown command '$command'.\n"
                ;;
        esac
    fi
done

exit 0
