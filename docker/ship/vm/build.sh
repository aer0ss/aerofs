#!/bin/bash
set -eu

die_usage() {
    echo >&2 "Usage: $0 <output_formats> <path_to_ship.yml> <path_to_extra_files> <path_to_output_folder> <product> ['nopush']"
    echo >&2 "       <output_formats> is a comma separated list of output formats. Only 'cloudinit', 'preloaded' and 'cloudinit_vm' are supported."
    echo >&2 "                Example: 'preloaded,cloudinit,cloudinit_vm'"
    echo >&2 "       <path_to_ship_yml> The path to the ship.yml that is used to configure the cloud-config file."
    echo >&2 "       <path_to_extra_files> The path to a folder that holds files to be copied to the root of the target host."
    echo >&2 "                The files are added to the cloud-config file so you may expect consistent results across all"
    echo >&2 "                output formats. Specify an empty string if no extra files are needed."
    echo >&2 "       <path_to_output_dir> The path to a folder that stores all the build artifacts i.e. the cloud-config file and the VMs."
    echo >&2 "       <0|1> This arg specifies if we should allow configurable registry for the product being built."
    echo >&2 "                Example: When building registry/registry_mirror, in no circumstance do we want configurable registry but"
    echo >&2 "                when building the appliance we want configurable registry for cloudinit_vm, cloudinit"
    echo >&2 "       'nopush' to skip pushing docker images to the local preload registry. Useful if the images are already"
    echo >&2 "                pushed and unchanged since the last build."
    exit 11
}

if [ $# != 6 ] && [ $# != 5 ]; then
    die_usage
else
    # Parse Output Formats
    OF_CLOUDINIT=0
    OF_PRELOADED=0
    OF_CLOUDINIT_VM=0
    for i in $(tr ',' ' ' <<< "$1"); do
        if [ "$i" = cloudinit ]; then
            OF_CLOUDINIT=1
        elif [ "$i" = preloaded ]; then
            OF_PRELOADED=1
        elif [ "$i" = cloudinit_vm ]; then
            OF_CLOUDINIT_VM=1
        else
            die_usage
        fi
    done
fi

SHIP_YML="$2"
EXTRA_FILES="$3"
OUTPUT="$4"
PROD_ALLOWS_CONFIGURABLE_REGISTRY="$5"

[[ "${6:-push}" = nopush ]] && PUSH=0 || PUSH=1

PRELOAD_SCRIPT="./preload-guest.sh"
# Use this global variable to pass data back to main()
GLOBAL_PRELOAD_REPO_URL=""

# Absolute paths are required by docker. (the realpath command is unavailable on OSX)
abspath() {
    (cd "$1" && pwd)
}

THIS_DIR=$(abspath "$(dirname ${BASH_SOURCE[0]})")

# See http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux
# for color codes.
GREEN='0;32'
CYAN='0;36'
YELLOW='1;33'
RED='0;31'
cecho() {
    echo >&2 -e "\033[$1m$2\033[0m"
}

setup_preload_registry() {

    # This function assumes:
    #
    #    - Local insecure registries are allowed (i.e. the "--insecure-registry 127.0.0.0/8" docker
    #      daemon option, which should be the default as of Docker 1.3.2.
    #    - All the container images including the loader are locally available on the `latest` tag.

    local REPO_CONTAINER=$1
    local LOADER_IMAGE=$2
    local PUSH=$3

    # Launch the preload registry. Create the container first if it doesn't exist.
    if [ $(docker ps -a | grep ${REPO_CONTAINER} | wc -l) = 0 ]; then
        docker create --name ${REPO_CONTAINER} --net=host registry
    fi

    # A potential bug of docker registry https://github.com/docker/docker-registry/issues/892 may
    # cause the container sometimes fail to start. So we keep restarting it until success.
    while true; do
        (set +e; docker start ${REPO_CONTAINER})
        sleep 3
        local RUNNING=$(docker inspect -f '{{ .State.Running }}' ${REPO_CONTAINER})
        [[ "${RUNNING}" = 'true' ]] && break
        echo >&2 "WARNING: ${REPO_CONTAINER} failed to start. Try again."
    done

    # Find the registry's hostname.
    # TODO use docker-machine for both CI and dev environment.
    local REPO_HOST
    if [ "$(grep '^tcp://' <<< "${DOCKER_HOST:-}")" ]; then
        # Use the hostname specified in DOCKER_HOST environment variable
        REPO_HOST=$(echo "${DOCKER_HOST}" | sed -e 's`^tcp://``' | sed -e 's`:.*$``')
    else
        # Find the first IP address of the local bridge
        local IFACE=$(ip route show 0.0.0.0/0 | awk '{print $5}')
        REPO_HOST=$(ip addr show ${IFACE} | grep '^ *inet ' | head -1 | tr / ' ' | awk '{print $2}')
    fi
    if [ -z "${REPO_HOST}" ]; then
        echo >&2 "ERROR: can't identify the registry's IP address" >&2
        exit 22
    fi

    local REPO_PORT=5000
    local REPO_URL=${REPO_HOST}:${REPO_PORT}
    echo "The Preload Registry is listening at ${REPO_URL}"

    if [ ${PUSH} = 1 ]; then
        # Push images to the preload registry
        for i in $(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_IMAGE} images); do
            PRELOAD="127.0.0.1:${REPO_PORT}/${i}"
            docker tag "${i}" "$PRELOAD"
            docker push "${PRELOAD}"
            docker rmi "${PRELOAD}"

            echo >&2 -e "\033[32mok: \033[0m- push ${PRELOAD}"
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
    local GENERATE_VDI="$2"
    local PRELOADED="$3"
    local TAG=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_IMAGE} tag)

    if [ -z "${TAG}" ]; then
        (set +x; cecho ${RED} "ERROR: couldn't read tag from Loader")
        exit 44
    fi

    # Copy the files to the output folder as the original file may be in a temp folder which can't
    # be reliably bind mounted by docker-machine.
    cp "${SHIP_YML}" "${OUTPUT}/ship.yml"
    local EXTRA_MOUNT="${OUTPUT}/extra"
    mkdir -p "${EXTRA_MOUNT}"
    rm -rf "${EXTRA_MOUNT}/*"
    if [ -n "${EXTRA_FILES}" ]; then
        cp -a "${EXTRA_FILES}"/* "${EXTRA_MOUNT}"
    fi

    local BUILD_WITH_CONFIGURABLE_REGISTRY=0
    # Build with configurable registry only if building a cloudinit vm for the appliance
    if [[ "$PROD_ALLOWS_CONFIGURABLE_REGISTRY" == "1" && (${GENERATE_VDI} == "1" && ${PRELOADED} == "0") ]]
    then
        BUILD_WITH_CONFIGURABLE_REGISTRY=1
    fi

    # Build the builder (how meta)
    local IMAGE=shipenterprise/vm-builder
    docker build -t ${IMAGE} "${THIS_DIR}/builder"
    echo >&2 -e "\033[32mok: \033[0m- build ${IMAGE}"

    # Run the builder. Need privilege to run losetup
    docker run --rm --privileged \
        -v "${OUTPUT}":/output \
        -v "${OUTPUT}/ship.yml":/ship.yml \
        -v "${EXTRA_MOUNT}":/extra \
        ${IMAGE} /run.sh ${GENERATE_VDI} /ship.yml /extra /output ${TAG} ${BUILD_WITH_CONFIGURABLE_REGISTRY}
}

resize_vdi() {
    VBoxManage modifyhd "$1" --resize $2
}

is_local_port_open() {
    # exec failure will terminate a non-interactive shell, hence the
    # use of a subshell. As a bonus it means we don't have to close
    # fd 3 since it's opened inside the subshell
    (exec 3<> "/dev/tcp/localhost/$1") &>/dev/null
}

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
    # Why suppress stderr? When no such VM is running, error output is annoying and we ignore the
    # errors anyway.
    VBoxManage controlvm ${VM} poweroff 2>/dev/null || true
    VBoxManage unregistervm ${VM} 2>/dev/null || true
    # Don't use 'unregistervm --delete' as the VM may refer to the vdi file we just created.
    # Deleting the VM would delete this file.
    rm -rf "${VM_BASE_DIR}"

    # Delete the VM folder in the default machine folder. The unregistervm above may destroy the
    # VM the user launches under the same VM name. Not deleting the folder may prevent the user
    # from launching VMs under the same name.
    local VM_FOLDER=$(VBoxManage list systemproperties \
        | grep '^Default machine folder:' \
        | sed 's/^Default machine folder:[ ]*\(.*\)/\1/')
    rm -rf "${VM_FOLDER}/${VM}"
}

is_vm_powered_off() {
    # Echo 1 if the VM is powered off, 0 otherwise.
    [[ $(VBoxManage showvminfo $1 | grep State | grep 'powered off') ]] && echo 1 || echo 0
}

insert_repo_file() {
    local REPO="$1"
    local SSH_ARGS="$2"
    ssh ${SSH_ARGS} "sudo mkdir -p /ship/loader/run && sudo sh -c \"echo \"$REPO\" > /ship/loader/run/repo\""
}

insert_and_run_preload_script() {

    local OVA="$1"
    local SSH_ARGS="$2"

    # Copy preload script to VM. Note:
    # 1. Don't save the script to /tmp. For some reason CoreOS may delete it while it's running.
    # 2. The "./" in the path is for the "sudo $PRELOAD_SCRIPT" below to work.
    ssh ${SSH_ARGS} "cat > ${PRELOAD_SCRIPT} && chmod u+x ${PRELOAD_SCRIPT}" < "${THIS_DIR}/preload-guest.sh"
    local START=$(date +%s)

    echo
    cecho ${CYAN} ">>> About to enter VM ($(date +%T)). You may access it from another terminal via:"
    echo
    cecho ${CYAN} "    ssh ${SSH_ARGS}"
    echo

    # Run preload script in VM. This step can take a while, and some times for some reason ssh may
    # disconnect in the middle. So we retry a few times.

    #ssh into vm to speed up docker pull
    ssh ${SSH_ARGS} top -b -d 1 &>/dev/null &
    TOP_PID=$!

    local DONE_FILE=repload.done
    local RETRY=0
    while true; do
        # Ignore exit code so the current script doesn't exit if ssh disconnect.
        ssh ${SSH_ARGS} "sudo ${PRELOAD_SCRIPT} ${GLOBAL_PRELOAD_REPO_URL} $(yml 'loader') $(yml 'repo') ${DONE_FILE}" || true
        if [ "$(ssh ${SSH_ARGS} "ls ${DONE_FILE}")" ]; then
            ssh ${SSH_ARGS} "sudo rm ${DONE_FILE} ${PRELOAD_SCRIPT}"
            break
        elif [ ${RETRY} = 3 ]; then
            cecho ${RED} "Preloading in SSH failed. Tried too many times. Gave up."
            kill -9 $TOP_PID
            exit 33
        else
            RETRY=$[${RETRY} + 1]
            cecho ${YELLOW} "Preloading in SSH failed. Retry #${RETRY}"
            # Let the system breathe a bit before retrying
            sleep 10
        fi
    done
    kill -9 $TOP_PID

    echo
    cecho ${CYAN} "<<< Exited from VM"
    echo
    cecho ${CYAN} "Preloading took $(expr \( $(date +%s) - ${START} \) / 60) minutes."
    echo
}

preload() {
    local VM=$1
    local SSH_FORWARD_PORT=$2
    local OVA="$3"
    local BUILD_VM_WITH_IMAGES=$4

    VBoxManage startvm ${VM} --type headless

    local KEY_FILE="${THIS_DIR}/builder/root/resources/preload-ssh.key"
    local SSH_ARGS="-q -p ${SSH_FORWARD_PORT} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no \
        -i ${KEY_FILE} core@localhost"

    # In case the permission is reverted (by git etc) restore it so ssh doesn't complain.
    chmod -f 400 "${KEY_FILE}"

    # Wait for ssh readiness
    echo "Waiting for VM to launch..."
    while [ monkey-$(ssh -o "ConnectTimeout 1" ${SSH_ARGS} echo magic) != monkey-magic ]; do sleep 1; done

    if [[ $BUILD_VM_WITH_IMAGES  == 1 ]]
    then
        insert_repo_file "$(yml 'repo')" "${SSH_ARGS}"
        insert_and_run_preload_script "${OVA}" "${SSH_ARGS}"
    fi

    # Overwrite cloud-config.yml, disable ssh, & shut down
    ssh ${SSH_ARGS} 'cat > tmp && sudo mv -f tmp /usr/share/oem/cloud-config.yml' < "${OUTPUT}/cloud-config.yml"
    ssh ${SSH_ARGS} "rm -rf ~core/.ssh && sudo systemd-run shutdown"

    echo "Wait for VM to shutdown..."
    while [ $(is_vm_powered_off ${VM}) = 0 ]; do sleep 1; done
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

    # Don't create the VM using FINAL_VM as its name: FINAL_VM may differ at each build (e.g.
    # changing version nubmers). Using a constant VM name (the VM variable) allows us to cleanly
    # remove the previous VM from a failed build.
    #
    # We therefore rename the VM to FINAL_NAME only before converting it to OVA, and rename it back
    # when done, so later steps can refer to the VM.
    #
    # Since the user may be running a VM with the same name as FINAL_VM, we use UUID to avoid
    # confusing VirtualBox.
    local UUID=$(VBoxManage list vms | grep "^\"${VM}\"" | tr '{' ' ' | tr '}' ' ' | awk '{print $2}')

    VBoxManage modifyvm ${UUID} --name ${FINAL_VM}
    VBoxManage export ${UUID} --manifest --output "${OVA}"
    VBoxManage modifyvm ${UUID} --name ${VM}

    "${THIS_DIR}/remove-vbox-section.py" "${OVA}"
}

# Return the value of the given key specified in ship.yml
yml() {
    grep "^$1:" "${SHIP_YML}" | sed -e "s/^$1: *//" | sed -e 's/ *$//'
}

find_free_local_port() {
    local PORT=2222
    while is_local_port_open ${PORT} ; do PORT=$(expr ${PORT} + 1); done
    echo ${PORT}
}

build_preloaded() {
    local LOADER_IMAGE=$1
    local VM_BASE_DIR="$2"
    local VM_IMAGE_NAME="$3"
    local OVA="$4"
    local GENERATE_VDI="$5"
    local BUILD_VM_WITH_IMAGES="$6"

    # Depending on the output format desired(cloudinit/preloaded/cloudinit_vm) we
    # call this function multiple times. This is becuase for preloaded/cloudinit_vm
    # we want separate VM's created and for each VM created we disable ssh.So just
    # create separate VDI's for each case.
    build_cloud_config_and_vdi ${LOADER_IMAGE} "${GENERATE_VDI}" "${BUILD_VM_WITH_IMAGES}"

    local VDI="${OUTPUT}/preloaded/disk.vdi"
    local PRELOAD_REPO_CONTAINER=shipenterprise-preload-registry

    resize_vdi "${VDI}" $(yml 'vm-disk-size')

    if [[ $BUILD_VM_WITH_IMAGES  = 1 ]]
    then
        setup_preload_registry ${PRELOAD_REPO_CONTAINER} ${LOADER_IMAGE} ${PUSH}
    fi

    # To minimize race condition, search for a free port right before we use the port.
    local SSH_FORWARD_PORT=$(find_free_local_port)
    local SSH_FORWARD_RULE=guestssh
    create_vm ${VM} $(yml 'vm-cpus') $(yml 'vm-ram-size') "${VDI}" "${VM_BASE_DIR}" ${SSH_FORWARD_PORT} ${SSH_FORWARD_RULE}
    preload ${VM} ${SSH_FORWARD_PORT} "${OVA}" $BUILD_VM_WITH_IMAGES
    update_nic ${VM} ${SSH_FORWARD_RULE}
    convert_to_ova ${VM} ${VM_IMAGE_NAME} "${OVA}"
    delete_vm ${VM} "${VM_BASE_DIR}"

    if [[ $BUILD_VM_WITH_IMAGES  = 1 ]]
    then
        teardown_preload_registry ${PRELOAD_REPO_CONTAINER}
    fi
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
    local CLOUD_CONFIG_OVA="${OUTPUT}/preloaded/${VM_IMAGE_NAME}-cloud-config.ova"

    if [ ${OF_PRELOADED} = 1 ]  || [ ${OF_CLOUDINIT_VM} = 1 ]; then
        # Since it's tricky to run VMs in containes, we run VirtualBox specific commands in the
        # host.
        delete_vm ${VM} "${VM_BASE_DIR}"
    fi

    if [ ${OF_CLOUDINIT} = 1 ]; then
        build_cloud_config_and_vdi ${LOADER_IMAGE} "0" "0"
    fi

    if [ ${OF_CLOUDINIT_VM} = 1 ]; then
        build_preloaded ${LOADER_IMAGE} "${VM_BASE_DIR}" "${VM_IMAGE_NAME}" "${CLOUD_CONFIG_OVA}" "1" "0"
    fi

    if [ ${OF_PRELOADED} = 1 ]; then
        build_preloaded ${LOADER_IMAGE} "${VM_BASE_DIR}" "${VM_IMAGE_NAME}" "${OVA}" "1" "1"
    fi

    echo
    cecho ${GREEN} "Build is complete."
    if [ ${OF_CLOUDINIT} = 1 ]; then
        cecho ${GREEN} "    VM cloud-config: ${OUTPUT}/cloud-config.yml"
    fi
    if [ ${OF_PRELOADED} = 1 ]; then
        cecho ${GREEN} "    VM preloaded:    $(du -h ${OVA})"
    fi
    if [ ${OF_CLOUDINIT_VM} = 1 ]; then
        cecho ${GREEN} "    VM cloud config vm:    $(du -h ${CLOUD_CONFIG_OVA})"
    fi
echo
}

main
