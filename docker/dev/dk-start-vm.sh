#!/bin/bash
set -eu

if [ $# -ne 1 ]
then
    echo "Usage: $0 <VM>"
    exit 1
fi

PWD="$( cd $(dirname $0) ; pwd -P )"

VM=$1

docker_machine_ls="$(docker-machine ls)"

if [ -n "$(echo $docker_machine_ls | grep ${VM})" ]
then
    if [ -n "$(echo $docker_machine_ls | grep ${VM}.*Stopped)" ]
    then
        echo "VM ${VM} was not running, starting it..."
        docker-machine start ${VM}

        # XXX
        # Only on Linux, we need to manually create a home folder mount for the current user.
        # This is not the case on Mac OS X.
        if [ "$(uname -s)" = "Linux" ]
        then
            echo "sudo mkdir -p $HOME && sudo mount -t vboxsf $(whoami) $HOME" | docker-machine ssh ${VM}
        fi
    else
        echo "VM ${VM} already started."
    fi
    $PWD/../../tools/cache/start.sh
else
    echo "VM ${VM} does not exist. Please run dk-create-vm."
    exit 1
fi
