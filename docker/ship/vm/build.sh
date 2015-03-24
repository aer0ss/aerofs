#!/bin/bash
set -ex

die_usage() {
    (set +x
        echo "Usage: $0 <output_formats> <path_to_ship.yml> <path_to_extra_files> <path_to_output_folder> ['nopush']"
        echo "       <output_formats> is a comma separated list of output formatS. Only 'cloudinit' and 'preloaded' are supported."
        echo "                Example: 'preloaded,cloudinit'"
        echo "       <path_to_extra_files> The path to a folder that holds files to be copied to the root of the target host."
        echo "                The files are added to the cloud-config file so you may expect consistent results across all"
        echo "                output formats. Specify an empty string if no extra files are needed."
        echo "       'nopush' to skip pushing docker images to the local preload registry. Useful if the images are already"
        echo "                pushed and unchanged since the last build."
        exit 11
    )
}

if [ $# != 5 ] && [ $# != 4 ]; then
    die_usage
else
    # Parse Output Formats
    OF_CLOUDINIT=0
    OF_PRELOADED=0
    for i in $(tr ',' ' ' <<< "$1"); do
        if [ "$i" = cloudinit ]; then
            OF_CLOUDINIT=1
        elif [ "$i" = preloaded ]; then
            OF_PRELOADED=1
        else
            die_usage
        fi
    done
fi

SHIP_YML="$2"
EXTRA_FILES="$3"
OUTPUT="$4"
[[ "$5" = nopush ]] && PUSH=0 || PUSH=1

# Absolute paths are required by docker. (the realpath command is unavailable on OSX)
abspath() {
    (cd "$1" && pwd)
}

THIS_DIR=$(abspath "$(dirname ${BASH_SOURCE[0]})")

# See http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux for color code
GREEN='0;32'
CYAN='0;36'
YELLOW='1;33'
RED='0;31'
cecho() {
    (set +x
        echo -e "\033[$1m$2\033[0m"
    )
}

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

    # A potential bug of docker registry https://github.com/docker/docker-registry/issues/892 may cause the container
    # sometimes fail to start. So we keep restarting it until success.
    while true; do
        docker start ${REPO_CONTAINER}
        sleep 3
        local RUNNING=$(docker inspect -f '{{ .State.Running }}' ${REPO_CONTAINER})
        [[ "${RUNNING}" = 'true' ]] && break
        echo "WARNING: ${REPO_CONTAINER} failed to start. Try again."
    done

    # Find the registry's hostname. TODO (WW) use docker-machine for both CI and dev environment
    local REPO_HOST
    if [ "$(grep '^tcp://' <<< "${DOCKER_HOST}")" ]; then
        # Use the hostname specified in DOCKER_HOST environment variable
        REPO_HOST=$(echo "${DOCKER_HOST}" | sed -e 's`^tcp://``' | sed -e 's`:.*$``')
    else
        # Find the first IP address of the local bridge
        local IFACE=$(ip route show 0.0.0.0/0 | awk '{print $5}')
        REPO_HOST=$(ip addr show ${IFACE} | grep '^ *inet ' | head -1 | tr / ' ' | awk '{print $2}')
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

build_cloud_config_and_vdi() {
    local LOADER_IMAGE=$1
    local TAG=$(docker run --rm ${LOADER_IMAGE} tag)

    if [ -z "${TAG}" ]; then
        (set +x; cecho ${RED} "ERROR: couldn't read tag from Loader")
        exit 44
    fi

    # Copy the files to the output folder as the original file may be in a temp folder which can't be
    # reliably bind mounted by docker-machine.
    cp "${SHIP_YML}" "${OUTPUT}/ship.yml"
    local EXTRA_MOUNT="${OUTPUT}/extra"
    mkdir -p "${EXTRA_MOUNT}"
    rm -rf "${EXTRA_MOUNT}/*"
    if [ -n "${EXTRA_FILES}" ]; then
        cp -a "${EXTRA_FILES}"/* "${EXTRA_MOUNT}"
    fi

    # Build the builder (how meta)
    local IMAGE=shipenterprise/vm-builder
    docker build -t ${IMAGE} "${THIS_DIR}/builder"

    # Run the builder. Need privilege to run losetup
    docker run --rm --privileged \
        -v "${OUTPUT}":/output \
        -v "${OUTPUT}/ship.yml":/ship.yml \
        -v "${EXTRA_MOUNT}":/extra \
        ${IMAGE} /run.sh ${OF_PRELOADED} /ship.yml /extra /output ${TAG}
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

    # Delete the VM folder in the default machine folder. the unregistervm above may destroy the VM the user launches
    # under the same VM name. Not deleting the folder may prevent the user from launching VMs under the same name.
    local VM_FOLDER=$(VBoxManage list systemproperties \
        | grep '^Default machine folder:' \
        | sed 's/^Default machine folder:[ ]*\(.*\)/\1/')
    rm -rf "${VM_FOLDER}/${VM}"
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

    local KEY_FILE="${THIS_DIR}/builder/root/resources/preload-ssh.key"
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

    # Copy preload script to VM. Note:
    # 1. Don't save the script to /tmp. For some reaon CoreOS may delete it while it's running.
    # 2. The "./" in the path is for the "sudo $PRELOAD_SCRIPT" below to work.
    local PRELOAD_SCRIPT=./preload-guest.sh
    ssh ${SSH_ARGS} "cat > ${PRELOAD_SCRIPT} && chmod u+x ${PRELOAD_SCRIPT}" < "${THIS_DIR}/preload-guest.sh"
    local START=$(date +%s)

    (set +x
        echo
        cecho ${CYAN} ">>> About to enter VM ($(date +%T)). You may access it from another terminal via:"
        echo
        cecho ${CYAN} "    $ ssh ${SSH_ARGS}"
        echo
    )

    # Run preload script in VM. This step can take a while, and some times for some reason ssh may disconnect in the
    # middle. So we retry a few times.
    local DONE_FILE=repload.done
    local RETRY=0
    while true; do
        # Ignore exit code so the current script doesn't exit if ssh disconnect.
        ssh ${SSH_ARGS} "sudo ${PRELOAD_SCRIPT} ${PRELOAD_REPO_URL} $(yml 'loader') $(yml 'repo') ${DONE_FILE}" || true
        if [ "$(ssh ${SSH_ARGS} "ls ${DONE_FILE}")" ]; then
            ssh ${SSH_ARGS} "sudo rm ${DONE_FILE} ${PRELOAD_SCRIPT}"
            break
        elif [ ${RETRY} = 3 ]; then
            cecho ${RED} "Preloading in SSH failed. Tried too many times. Gave up."
            exit 33
        else
            RETRY=$[${RETRY} + 1]
            cecho ${YELLOW} "Preloading in SSH failed. Retry #${RETRY}"
            # Let the system breathe a bit before retrying
            sleep 10
        fi
    done

    (set +x
        echo
        cecho ${CYAN} "<<< Exited from VM"
        echo
        cecho ${CYAN} "Preloading took $(expr \( $(date +%s) - ${START} \) / 60) minutes."
        echo
    )

    # Overwrite cloud-config.yml, disable ssh, & shut donw
    ssh ${SSH_ARGS} 'cat > tmp && sudo mv -f tmp /usr/share/oem/cloud-config.yml' < "${OUTPUT}/cloud-config.yml"
    ssh ${SSH_ARGS} "rm -rf ~core/.ssh && sudo shutdown 0"

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

    "${THIS_DIR}/../../../packaging/bakery/private-deployment/remove_vbox_section_from_ova.py" "${OVA}"
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

build_preloaded() {
    local LOADER_IMAGE=$1
    local VM_BASE_DIR="$2"
    local VM_IMAGE_NAME="$3"
    local OVA="$4"

    local VDI="${OUTPUT}/preloaded/disk.vdi"
    local PRELOAD_REPO_CONTAINER=shipenterprise-preload-registry

    resize_vdi "${VDI}" $(yml 'vm-disk-size')

    setup_preload_registry ${PRELOAD_REPO_CONTAINER} ${LOADER_IMAGE} ${PUSH}

    # To minimize race condition, search for a free port right before we use the port.
    local SSH_FORWARD_PORT=$(find_free_local_port)
    local SSH_FORWARD_RULE=guestssh
    create_vm ${VM} $(yml 'vm-cpus') $(yml 'vm-ram-size') "${VDI}" "${VM_BASE_DIR}" ${SSH_FORWARD_PORT} ${SSH_FORWARD_RULE}
    preload ${VM} ${SSH_FORWARD_PORT} ${GLOBAL_PRELOAD_REPO_URL}
    update_nic ${VM} ${SSH_FORWARD_RULE}
    convert_to_ova ${VM} ${VM_IMAGE_NAME} "${OVA}"
    delete_vm ${VM} "${VM_BASE_DIR}"

    teardown_preload_registry ${PRELOAD_REPO_CONTAINER}
}

main() {
    # Clobber the output folder as it will be sent to docker daemon as context and it may
    # contain huge files.
    rm -rf "${OUTPUT}"
    mkdir -p "${OUTPUT}"
    OUTPUT=$(abspath "${OUTPUT}")

    local LOADER_IMAGE=$(yml 'loader')
    local VM=shipenterprise-build-vm
    local VM_IMAGE_NAME=$(yml 'vm-image-name')
    local VM_BASE_DIR="${OUTPUT}/preloaded/vm"
    local OVA="${OUTPUT}/preloaded/${VM_IMAGE_NAME}.ova"

    if [ ${OF_PRELOADED} = 1 ]; then
        # Since it's tricky to run VMs in containes, we run VirtualBox specific commands in the host.
        delete_vm ${VM} "${VM_BASE_DIR}"
    fi

    if [ ${OF_CLOUDINIT} = 1 ] || [ ${OF_PRELOADED} = 1 ]; then
        build_cloud_config_and_vdi ${LOADER_IMAGE}
    fi

    if [ ${OF_PRELOADED} = 1 ]; then
        build_preloaded ${LOADER_IMAGE} "${VM_BASE_DIR}" "${VM_IMAGE_NAME}" "${OVA}"
    fi

    (set +x
        echo
        cecho ${GREEN} "Build is complete."

        if [ ${OF_CLOUDINIT} = 1 ]; then
            echo
            cecho ${GREEN} "    VM cloud-config:        ${OUTPUT}/cloud-config.yml"
        fi

        if [ ${OF_PRELOADED} = 1 ]; then
            echo
            cecho ${GREEN} "    VM preloaded:           $(du -h ${OVA})"
        fi

        echo
    )
}

main
