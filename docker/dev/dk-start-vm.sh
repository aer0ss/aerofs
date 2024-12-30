#!/bin/bash
set -eu

if [ $# -ne 1 ]
then
    echo >&2 "Usage: $0 <VM>"
    exit 1
fi

VM=$1
THIS_DIR="$( cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker_machine_ls="$(colima list | grep "${VM}" || true)"

if [ -n "${docker_machine_ls}" ]
then
    if [ -z "$(echo ${docker_machine_ls} | grep Running)" ]
    then
        echo "VM ${VM} was not running, starting it..."
        colima start ${VM}
        # FIXME: fix share.syncfs.com hostname *inside VM* if machine IP changed...
    else
        echo "VM ${VM} already started."
    fi

    # this adjusts share.syncfs.com hostname *on the host*
    $THIS_DIR/dk-host-ip.sh
else
    echo >&2 "VM ${VM} does not exist. Please run dk-create-vm."
    exit 1
fi
