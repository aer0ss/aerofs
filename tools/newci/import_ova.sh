#!/bin/bash
set -e -x

function DieUsage()
{
    echo Usage: $0 ova_file >&2
    exit 1
}

[[ $# -eq 1 ]] || DieUsage

ovapath="$1"
vmname="aerofs-appliance"
aerofs_root_dir="$(git rev-parse --show-toplevel)"
cloudinit_dir="$aerofs_root_dir/packaging/bakery/development/cloudinit"

pushd "$cloudinit_dir" >/dev/null
make clean newci.iso
popd >/dev/null

vboxmanage import "$ovapath" --vsys 0 --vmname $vmname
vboxmanage storagectl $vmname --add sata --name "SATACI" --sataportcount 1
vboxmanage storageattach $vmname --storagectl "SATACI" --port 1 --device 0 --type dvddrive --medium "$cloudinit_dir/newci.iso"
VBoxManage startvm $vmname --type headless
