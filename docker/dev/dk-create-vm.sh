#!/bin/bash
set -eu

if [ $# -ne 1 ]
then
    echo "Usage: $0 <VM>"
    exit 1
fi

VM=$1

if [ -z "$(docker-machine ls | grep \"${VM}\")" ]
then
    docker-machine create -d virtualbox --virtualbox-disk-size 50000 --virtualbox-memory 3072 ${VM}

    # XXX
    # Only on Linux, we need to manually create a home folder mount for the current user.
    # This is not the case on Mac OS X.
    if [ "$(uname -s)" = "Linux" ]
    then
        # The docker machine must be stopped for the shared folder creation op.
        docker-machine stop ${VM}
        VBoxManage sharedfolder add "docker-dev" --name $(whoami) --hostpath $HOME --automount
        docker-machine start ${VM}
        echo "sudo mkdir $HOME && sudo mount -t vboxsf $(whoami) $HOME" | docker-machine ssh ${VM}
    fi
fi
