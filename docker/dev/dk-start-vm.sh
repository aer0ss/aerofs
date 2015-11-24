#!/bin/bash
set -eu

if [ $# -ne 1 ]
then
    echo "Usage: $0 <VM>"
    exit 1
fi

VM=$1

docker_machine_ls="$(docker-machine ls | grep "${VM}" || true)"

if [ -n "${docker_machine_ls}" ]
then
    if [ -z "$(echo ${docker_machine_ls} | grep Running)" ]
    then
        echo "VM ${VM} was not running, starting it..."
        docker-machine start ${VM}

        # FIXME: docker machine 0.4+ use tmpfs for / ?!?!?!?!
        # this means change to most of /etc, and crucially to docker daemon
        # config, are lost on reboot
        THIS_DIR="$( cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )"
        $THIS_DIR/../../tools/cache/start.sh

        # XXX
        # Only on Linux, we need to manually create a home folder mount for the current user.
        # This is not the case on Mac OS X.
        # FIXME: should not be necessary with docker machine 0.4+
        if [ "$(uname -s)" = "Linux" ]
        then
            echo "sudo mkdir -p $HOME && sudo mount -t vboxsf $(whoami) $HOME" | docker-machine ssh ${VM}
        fi
    else
        echo "VM ${VM} already started."
    fi
else
    echo "VM ${VM} does not exist. Please run dk-create-vm."
    exit 1
fi
