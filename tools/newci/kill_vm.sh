#!/bin/bash

function DieUsage {
    echo "Usage: $0 [vm-name]"
    exit 1
}

[[ $# -eq 1 ]] || DieUsage

vmname=$1

function haltvm { echo Halting VM...; VBoxManage controlvm $1 poweroff; }
function destroyvm { echo Destroying VM...; VBoxManage unregistervm $1 --delete 2>/dev/null || true; }

VBoxManage list runningvms | grep \"$vmname\" 1>/dev/null 2>&1
[[ $? -eq 0 ]] && haltvm $vmname


VBoxManage list vms | grep \"$vmname\" 1>/dev/null 2>&1
[[ $? -eq 0 ]] && destroyvm $vmname

# This is sadly necessary because VirtualBox is unintelligent about the order
# in which it does things. Sometimes it will remove the VM from its database
# but not delete this folder (so it has no idea this exists, and falls over when
# you then try to create a new VM of the same name
echo Deleting machine folder...
default_mf=$(VBoxManage list systemproperties \
    | grep '^Default machine folder:' \
    | sed 's/^Default machine folder:[ ]*\(.*\)/\1/')
rm -rf "$default_mf/$vmname"

echo Success!
