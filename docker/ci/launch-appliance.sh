#!/bin/bash
set -e

if [ $# != 2 ]; then
    echo "This script launches the preloaded AeroFS appliance VM at the given IP. A NAT interface is also created so"
    echo "the VM can use the CI host's dnsmasq nameserver to resolve its own hostname (e.g. share.syncfs.com)"
    echo "Usage: $0 <ip> <gateway>"
    echo
    echo "e.g.   $0 1.2.3.4/8 1.1.1.1"
    exit 11
fi
IP_AND_PREFIX="$1"
IP=$(sed -e 's!/.*!!' <<< ${IP_AND_PREFIX})
GATEWAY="$2"

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"

# See https://github.com/coreos/coreos-cloudinit/blob/master/Documentation/config-drive.md
create_cloud_config_drive() {
    local CLOUD_DRIVE_ISO="$1"
    local TMP="$(mktemp -dt ci-user-data-XXX)"
    local USER_DATA="${TMP}/openstack/latest/user_data"
    local SSH_PUB="$(cat "${THIS_DIR}/ci-ssh.pub")"
    mkdir -p "$(dirname "${USER_DATA}")"

    sed -e "s!{{ ip_and_prefix }}!${IP_AND_PREFIX}!" \
        -e "s!{{ ip }}!${IP}!" \
        -e "s!{{ gateway }}!${GATEWAY}!" \
        -e "s!{{ ssh_pub }}!${SSH_PUB}!" \
        "${THIS_DIR}/ci-cloud-config.jinja" \
        > "${USER_DATA}"

    # Label "config-2" is mantatory
    genisoimage -R -V config-2 -o "${CLOUD_DRIVE_ISO}" "${TMP}"
    rm -rf "${TMP}"
}

delete_vm() {
    local VM=$1

    # Why suppress stderr? When no such VM is running, error output is annonying and we ignore the errors anyway
    VBoxManage controlvm ${VM} poweroff 2>/dev/null || true
    VBoxManage unregistervm --delete ${VM} 2>/dev/null || true

    # Delete machine folder. VirtualBox may have left garbage there preventing us from reusing the same VM name.
    local VM_FOLDER=$(VBoxManage list systemproperties \
        | grep '^Default machine folder:' \
        | sed -e 's/^Default machine folder:[ ]*\(.*\)/\1/')
    rm -rf "${VM_FOLDER}/${VM}"
}

launch_vm() {
    local VM=$1
    local OVA="$2"
    local CLOUD_DRIVE_ISO="$3"
    local SSH_FORWARD_PORT="$4"

    VBoxManage import "${OVA}" --vsys 0 --vmname ${VM}
    # Assume the OVA already has an IDE controller
    VBoxManage storageattach ${VM} --storagectl "IDE Controller" --port 1 --device 0 --type dvddrive --medium "${CLOUD_DRIVE_ISO}"
    # Create a NAT iface so the VM can use the CI host's dnsmasq nameserver to resolve its own hostname
    VBoxManage modifyvm ${VM} --nic8 nat --natpf8 ssh,tcp,,${SSH_FORWARD_PORT},,22

    VBoxManage startvm ${VM} --type headless
}

wait_for_url() {
    local PORT=$1
    local URL="http://${IP}:${PORT}"
    (set +x
        echo "Waiting for ${URL} readiness..."
        while true; do
            BODY="$(curl -s --connect-timeout 1 ${URL} || true)"
            [[ "${BODY}" ]] && break
            sleep 1
        done
    )
}

main() {
    # VirtualBox requires .iso file extension
    local CLOUD_DRIVE_ISO=$(mktemp -t ci-cloud-drive-XXX.iso)

    echo "Creating cloud-config drive..."
    create_cloud_config_drive ${CLOUD_DRIVE_ISO}

    echo "Deleting old VM..."
    local VM="appliance-under-test"
    delete_vm ${VM}

    echo "Launching new VM..."
    # The "docker-appliance-" prefix must be consistent with ship.yml.jinja
    local OVA=${THIS_DIR}/../../out.ship/preloaded/docker-appliance-*.ova

    # For some reaon "vboxmanage import" doesn't handle '..' in paths very well. So convert to real path.
    # (OSX has no realpath command.)
    OVA="$(cd $(dirname "${OVA}"); pwd)/$(basename "${OVA}")"

    local SSH_FORWARD_PORT=54365
    launch_vm ${VM} ${OVA} ${CLOUD_DRIVE_ISO} ${SSH_FORWARD_PORT}

    # Use absolute path so users of the echo'ed command below can run the command anywhere.
    local KEY_FILE="$(cd "${THIS_DIR}" && pwd)/ci-ssh.key"
    # In case the permission is reverted (by git etc) restore it so ssh doesn't complain.
    chmod -f 400 "${KEY_FILE}"

    echo
    echo ">>> VM has launched. You may access it from another terminal via:"
    echo
    echo "    $ ssh -q -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i ${KEY_FILE} -p ${SSH_FORWARD_PORT} core@$localhost"
    echo

    # Not until the Web UIs is ready we can declare a success launch.
    wait_for_url 80

    echo
    echo ">>> About to wait for Signup Decoder service. It's being built from scratch and may take a while."
    echo "SSH into the VM via the above command and to monitor progress using:"
    echo
    echo "    vm$ systemctl status -l signup-decoder"
    echo "    vm$ journalctl -fu signup-decoder"
    echo
    wait_for_url 21337
}

main