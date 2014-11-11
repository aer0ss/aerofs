#!/bin/bash
set -e -x

function DieUsage {
    echo Usage: $0 [ova file] [adapter] >&2
    echo Example: $0 aerofs-private-deployment.ova eth0 >&2
    exit 1
}

[[ $# -eq 2 ]] || DieUsage

aerofs_root="$(dirname $0)"/../..

ovapath="$1"
shortadpt="$2"
properties_file="$aerofs_root/packaging/bakery/development/config/external.properties"
license_file="$aerofs_root/packaging/bakery/development/test.license"
vmname="ci-servers"
fwport=2122

function startvm { echo Starting VM...; VBoxManage startvm "$1" --type headless; }
function longadpt { VBoxManage list bridgedifs | grep ^Name: | grep -o "$1:.*" || echo $1; }

"$(dirname $0)/kill_vm.sh" $vmname

# Prepare iso image for cloudinit
iso_build_dir="$aerofs_root/packaging/bakery/development/cloudinit"
pushd $iso_build_dir
make clean
make newci.iso
popd

VBoxManage import "$ovapath" --vsys 0 --vmname $vmname
# sets UUID to environment
eval $(VBoxManage showvminfo --machinereadable $vmname | grep ^UUID=)
VBoxManage modifyvm $UUID --nic1 nat \
                            --natpf1 guestssh,tcp,,$fwport,,22 \
                            --nic2 bridged \
                            --bridgeadapter2 "$(longadpt $shortadpt)"
# attaches CD drive and CD image to VM
VBoxManage storagectl $UUID --name "SATACI" --add sata
VBoxManage storageattach $UUID --storagectl "SATACI" --port 1 --device 0 --type dvddrive --medium "$iso_build_dir/newci.iso"
# boot
startvm $UUID

# wait for init to complete
echo Waiting 60s for init to complete...
sleep 60s

echo Provisioning VM with your public key...
SSH_OPTS="-o Loglevel=FATAL -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes"

set +e
# We will attempt to push the insecure private key every 10 seconds for a maximum of 5 minutes.
let attempts=0
while [ 1 ]; do
    sleep 10
    ssh -p $fwport -i ~/.vagrant.d/insecure_private_key $SSH_OPTS ubuntu@localhost "echo $(cat ~/.ssh/id_rsa.pub) >> ~/.ssh/authorized_keys"
    ret=$?
    if [ $ret -eq 0 ]; then
        break
    fi
    attempts=$((attempts+1))
    if [ $attempts -gt 30 ]; then
        echo "FAILED: could not provision insecure private key after $attempts attempts."
        exit 1
    fi
done
set -e

echo Reading license file...
license_data=$(cat "$license_file" | python -c 'import sys,urllib,base64; print urllib.urlencode({"license_file": base64.urlsafe_b64encode(sys.stdin.read())})')

echo Provisioning everything else...
scp -P $fwport $SSH_OPTS "$properties_file" ubuntu@localhost:external.properties
ssh -p $fwport $SSH_OPTS ubuntu@localhost <<EOSSH

set -ex
sudo cp external.properties /opt/config/properties/

curl --insecure --request POST --data "$license_data" http://localhost:5434/set_license_file

sudo ln -s /etc/nginx/backends-available/aerofs-polaris /etc/nginx/backends-enabled/aerofs-polaris
sudo nginx -s reload

sudo aerofs-bootstrap-taskfile /opt/bootstrap/tasks/apply-config.tasks
sudo aerofs-bootstrap-taskfile /opt/bootstrap/tasks/set-configuration-initialized.tasks

sudo cat /etc/iptables/rules.v4 | sudo iptables-restore
sudo iptables -I INPUT 4 -p tcp -m multiport --dports 21337 -m comment --comment "signup code tools" -j ACCEPT
sudo iptables-save | sudo tee /etc/iptables/rules.v4

ip addr | grep 'inet 192' | sed -E 's/\s*inet (192(\.[0-9]{1,3}){3}).*/\1/g'

EOSSH

