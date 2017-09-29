#!/bin/bash
set -eu

if [ $# -ne 1 ]
then
    echo >&2 "Usage: $0 <VM>"
    exit 1
fi

VM=$1

if [ -z "$(docker-machine ls | grep ${VM})" ]
then
    # NB: explicit boot2docker URL is required for now
    # docker 1.13 removed support for pushing to v1 registries but registry.aerofs.com is still using v1
    docker-machine create -d virtualbox --virtualbox-disk-size 50000 \
        --virtualbox-boot2docker-url https://github.com/boot2docker/boot2docker/releases/download/v1.12.5/boot2docker.iso \
        --virtualbox-memory 3072 --virtualbox-cpu-count 2 ${VM}

    # XXX
    # Only on Linux, we need to manually create a home folder mount for the current user.
    # This is not the case on Mac OS X.
    # FIXME: this should no longer be necessary with docker-machine 0.4+
    if [ "$(uname -s)" = "Linux" ]
    then
        # The docker machine must be stopped for the shared folder creation op.
        docker-machine stop ${VM}
        VBoxManage sharedfolder add "docker-dev" --name $(whoami) --hostpath $HOME --automount
        docker-machine start ${VM}
        echo "sudo mkdir $HOME && sudo mount -t vboxsf $(whoami) $HOME" | docker-machine ssh ${VM}
    fi
fi
