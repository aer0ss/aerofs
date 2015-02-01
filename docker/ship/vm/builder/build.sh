#!/bin/bash
set -ex

if [ $# != 3 ] && [ $# != 2 ]; then
    (set +x
        echo "Usage: $0 <path_to_ship.yml> <path_to_output_folder> [nopush]"
        echo "       'nopush' to skip pushing docker images to the local preload registry. Useful if the images are already"
        echo "       pushed and unchanged since the last build."
        exit 11
    )
fi
SHIP_YML="$1"
OUTPUT="$2"
[[ x"$3" = xnopush ]] && PUSH=0 || PUSH=1

# Absolute paths are required by docker. (the realpath command is unavailable on OSX)
abspath() {
    (cd "$1" && pwd)
}

THIS_DIR=$(abspath "$(dirname ${BASH_SOURCE[0]})")

# Use this global variable to pass data back to main()
GLOBAL_PRELOAD_REPO_URL=
setup_preload_registry() {

    # This function assumes:
    #
    #    - Local insecure registries are allowed (i.e. the "--insecure-registry 127.0.0.0/8" docker daemon
    #      option, which should be the default as of Docker 1.3.2.
    #    - all the container images including the loader are locally available on the `latest` tag.

    local REPO_CONTAINER=$1
    local LOADER_IMAGE=$2
    local PUSH=$3

    # Launch the preload registry. Create the container first if it doesn't exist.
    if [ $(docker ps -a | grep ${REPO_CONTAINER} | wc -l) = 0 ]; then
        docker create -P --name ${REPO_CONTAINER} registry
    fi
    docker start ${REPO_CONTAINER}

    # Find the registry's hostname
    local REPO_HOST
    if [ "$(grep '^tcp://' <<< "${DOCKER_HOST}")" ]; then
        # Use the hostname specified in DOCKER_HOST
        REPO_HOST=$(echo "${DOCKER_HOST}" | sed -e 's`^tcp://``' | sed -e 's`:.*$``')
    else
        # Find out the IP address of the local bridge
        local IFACE=$(ip route show 0.0.0.0/0 | awk '{print $5}')
        REPO_HOST=$(ip addr show ${IFACE} | grep '^ *inet ' | tr / ' ' | awk '{print $2}')
    fi
    if [ -z "${REPO_HOST}" ]; then
        echo "ERROR: can't identify the registry's IP address" >&2
        exit 22
    fi

    local REPO_PORT=$(docker port ${REPO_CONTAINER} 5000 | sed -e s'/.*://')
    local REPO_URL=${REPO_HOST}:${REPO_PORT}
    echo "The Preload Registry is listening at ${REPO_URL}"

    if [ ${PUSH} = 1 ]; then
        # Push images to the preload registry
        for i in $(docker run --rm ${LOADER_IMAGE} images); do
            (set +x
                echo "============================================================"
                echo " Pushing ${i} to local preload registry..."
                echo "============================================================"
            )
            PRELOAD="127.0.0.1:${REPO_PORT}/${i}"
            docker tag -f "${i}" "$PRELOAD"
            docker push "${PRELOAD}"
            docker rmi "${PRELOAD}"
        done
    fi

    GLOBAL_PRELOAD_REPO_URL=${REPO_URL}
}

teardown_preload_registry() {
    # Keep the container around so we can reuse the image cache in later builds.
    docker stop $1
}

build_vdi() {
    # Copy ship.yml to the output folder as the original file may be in a temp folder which can't be
    # reliably bind mounted by boot2docker.
    cp "${SHIP_YML}" "${OUTPUT}/ship.yml"

    # Build the builder (how meta)
    local IMAGE=shipenterprise/vm-builder
    docker build -t ${IMAGE} "${THIS_DIR}"

    # Run the builder. Need privilege to run losetup
    docker run --rm --privileged -v "${OUTPUT}":/output -v "${OUTPUT}/ship.yml":/ship.yml \
        ${IMAGE} /run.sh /ship.yml /output
}

resize_vdi() {
    VBoxManage modifyhd "$1" --resize $2
}

is_local_port_open() {
    # See http://bit.ly/1vDblqg
    exec 3<> "/dev/tcp/localhost/$1"
    CODE=$?
    exec 3>&- # close output
    exec 3<&- # close input
    [[ ${CODE} = 0 ]] && echo 1 || echo 0
}

# PREDOCKER: Most of this was adopted from make_ova.sh. Make sure they're still in sync
# when deleting make_ova.sh.
create_vm() {
    local VM=$1
    local CPUS=$2
    local RAM=$3
    local DISK="$4"
    local VM_BASE_DIR="$5"
    local SSH_FORWARD_PORT=$6
    local SSH_FORWARD_RULE=$7

    # Create a NAT adapter with port forwarding so we can ssh into the VM.
    VBoxManage createvm --register --name ${VM} --ostype Linux_64 --basefolder "${VM_BASE_DIR}"
    VBoxManage modifyvm ${VM} --cpus ${CPUS} --memory ${RAM} --nic1 nat \
        --natpf1 "${SSH_FORWARD_RULE},tcp,127.0.0.1,${SSH_FORWARD_PORT},,22"

    # Attach the disk
    VBoxManage storagectl ${VM} --name IDE --add ide
    VBoxManage storageattach ${VM} --storagectl IDE --port 0 --device 0 --type hdd --medium "${DISK}"
}

delete_vm() {
    local VM=$1
    local VM_BASE_DIR="$2"
    # Why suppress stderr? When no such VM is running, error output is annonying and we ignore the errors anyway
    VBoxManage controlvm ${VM} poweroff 2>/dev/null || true
    VBoxManage unregistervm ${VM} 2>/dev/null || true
    # Don't use 'unregistervm --delete' as the VM may refer to the vdi file we just created. Deleting the VM would
    # delete this file.
    rm -rf "${VM_BASE_DIR}"
}

is_vm_powered_off() {
    # Echo 1 if the VM is powered off, 0 otherwise.
    [[ $(VBoxManage showvminfo $1 | grep State | grep 'powered off') ]] && echo 1 || echo 0
}

preload() {
    local VM=$1
    local SSH_FORWARD_PORT=$2
    local PRELOAD_REPO_URL=$3

    VBoxManage startvm ${VM} --type headless

    local KEY_FILE="${THIS_DIR}/root/resources/preload-ssh.key"
    local SSH_ARGS="-q -p ${SSH_FORWARD_PORT} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no \
        -i ${KEY_FILE} core@localhost"

    # In case the permission is reverted (by git etc) restore it so ssh doesn't complain.
    chmod -f 400 "${KEY_FILE}"

    # Wait for ssh readiness
    (set +x
        echo "Waiting for VM to launch..."
        # Wait for VM sshd to be ready
        while [ monkey-$(ssh -o "ConnectTimeout 1" ${SSH_ARGS} echo magic) != monkey-magic ]; do sleep 1; done
    )

    # Copy preload script to VM
    local PRELOAD_SCRIPT=/tmp/preload-guest.sh
    ssh ${SSH_ARGS} "cat > ${PRELOAD_SCRIPT} && chmod u+x ${PRELOAD_SCRIPT}" < "${THIS_DIR}/preload-guest.sh"
    local START=$(date +%s)

    (set +x
        echo
        cecho ${CYAN} ">>> About to enter VM ($(date +%T)). You may access it from another terminal via:"
        echo
        cecho ${CYAN} "    $ ssh ${SSH_ARGS}"
        echo
    )

    # Run preload script in VM
    ssh ${SSH_ARGS} "sudo ${PRELOAD_SCRIPT} ${PRELOAD_REPO_URL} $(yml 'loader-image') $(yml 'repo')"

    (set +x
        echo
        cecho ${CYAN} "<<< Exited from VM"
        echo
        cecho ${CYAN} "Preloading took $(expr \( $(date +%s) - ${START} \) / 60) minutes."
        echo
    )

    # Overwrite cloud-config.yml, disable ssh, & shut donw
    ssh ${SSH_ARGS} 'cat > tmp && sudo mv -f tmp /usr/share/oem/cloud-config.yml' < "${OUTPUT}/cloud-config.yml"
    ssh ${SSH_ARGS} "rm ${PRELOAD_SCRIPT} && rm -rf ~core/.ssh && sudo shutdown 0"

    (set +x
        echo "Wait for VM to shutdown..."
        while [ $(is_vm_powered_off ${VM}) = 0 ]; do sleep 1; done
    )
}

update_nic() {
    VM=$1
    SSH_FORWARD_RULE=$2

    # A bridged e1000 NIC connected to first host interface for easy testing. It replaces NAT.
    local BRIDGE=$(VBoxManage list --long bridgedifs | grep ^Name: | sed 's/^[^ ]* *//' | head -n 1)

    VBoxManage modifyvm ${VM} --natpf1 delete ${SSH_FORWARD_RULE}
    VBoxManage modifyvm ${VM} --nic1 bridged --nictype1 82545EM --bridgeadapter1 "${BRIDGE}"
}

convert_to_ova() {
    local VM="$1"
    local FINAL_VM="$2"
    local OVA="$3"

    # Design notes:
    #
    # Don't create the VM using FINAL_VM as its name: FINAL_VM may differ at each build (e.g. changing version nubmers).
    # Using a constant VM name (the VM variable) allows us to cleanly remove the previous VM from a failed build.
    #
    # We therefore rename the VM to FINAL_NAME only before converting it to OVA, and rename it back when done, so later
    # steps can refer to the VM.
    #
    # Since the user may be running a VM with the same name as FINAL_VM, we use UUID to avoid confusing VirtualBox.
    #
    local UUID=$(VBoxManage list vms | grep "^\"${VM}\"" | tr '{' ' ' | tr '}' ' ' | awk '{print $2}')

    VBoxManage modifyvm ${UUID} --name ${FINAL_VM}
    VBoxManage export ${UUID} --manifest --output "${OVA}"
    VBoxManage modifyvm ${UUID} --name ${VM}

    "${THIS_DIR}/../../../../packaging/bakery/private-deployment/remove_vbox_section_from_ova.py" "${OVA}"
}

# Return the value of the given key specified in ship.yml
yml() {
    grep "^$1:" "${SHIP_YML}" | sed -e "s/^$1: *//" | sed -e 's/ *$//'
}

find_free_local_port() {
    local PORT=2222
    while [ $(is_local_port_open ${PORT}) = 1 ]; do PORT=$(expr ${PORT} + 1); done
    echo ${PORT}
}

# Colorful echo
GREEN='0;32'
CYAN='0;36'
cecho() {
    (set +x
        echo -e "\033[$1m$2\033[0m"
    )
}

main() {
    # Clobber the output folder as it will be sent to docker daemon as context and it may
    # contain huge files.
    rm -rf "${OUTPUT}"
    mkdir -p "${OUTPUT}"
    OUTPUT=$(abspath "${OUTPUT}")

    local PRELOAD_REPO_CONTAINER=shipenterprise-preload-registry
    local VM=shipenterprise-build-vm
    local FINAL_VM=$(yml 'vm-image-name')
    local VM_BASE_DIR="${OUTPUT}/preloaded/vm"
    local VDI="${OUTPUT}/preloaded/disk.vdi"
    local OVA="${OUTPUT}/preloaded/${FINAL_VM}.ova"

    setup_preload_registry ${PRELOAD_REPO_CONTAINER} $(yml 'loader-image') ${PUSH}

    # Since it's tricky to run VMs in containes, we run VirtualBox specific commands in the host.
    delete_vm ${VM} "${VM_BASE_DIR}"
    build_vdi
    resize_vdi "${VDI}" $(yml 'vm-disk-size')

    # To minimize race condition, search for a free port right before we use the port.
    local SSH_FORWARD_PORT=$(find_free_local_port)
    local SSH_FORWARD_RULE=guestssh
    create_vm ${VM} $(yml 'vm-cpus') $(yml 'vm-ram-size') "${VDI}" "${VM_BASE_DIR}" ${SSH_FORWARD_PORT} ${SSH_FORWARD_RULE}
    preload ${VM} ${SSH_FORWARD_PORT} ${GLOBAL_PRELOAD_REPO_URL}
    update_nic ${VM} ${SSH_FORWARD_RULE}
    convert_to_ova ${VM} ${FINAL_VM} "${OVA}"
    delete_vm ${VM} "${VM_BASE_DIR}"

    teardown_preload_registry ${PRELOAD_REPO_CONTAINER}

    (set +x
        echo
        cecho ${GREEN} "Image is ready:"
        echo
        cecho ${GREEN} "    $(du -h ${OVA})"
        echo
    )
}

main
