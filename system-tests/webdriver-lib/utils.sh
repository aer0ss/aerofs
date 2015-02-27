#!/bin/bash

function test_port() {
    # See http://bit.ly/1vDblqg
    exec 3<> "/dev/tcp/$1/$2"
    CODE=$?
    exec 3>&- # close output
    exec 3<&- # close input
    echo ${CODE}
}

function wait_port() {
    echo "Waiting for $1:$2 readiness..."
    while [ $(test_port $1 $2 2> /dev/null) != 0 ]; do
    	sleep 1
    done
}

# For color code see http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux
GREEN='0;32'
CYAN='0;36'
YELLOW='1;33'
RED='0;31'
cecho() { echo -e "\033[$1m$2\033[0m"; }
info() { cecho ${CYAN} "$1"; }
success() { cecho ${GREEN} "$1"; }
warn() { cecho ${YELLOW} "$1"; }
error() { cecho ${RED} "$1"; }
