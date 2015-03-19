#!/bin/bash
set -e

# See http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux for color code
GREEN='0;32'
CYAN='0;36'
YELLOW='1;33'
RED='0;31'
cecho() { echo -e "\033[$1m$2\033[0m"; }
info() { cecho ${CYAN} "$1"; }
success() { cecho ${GREEN} "$1"; }
error() { cecho ${RED} "$1"; }

if [ $# = 0 ]; then
    BUILD=1
elif [ "$1" = nobuild ]; then
    BUILD=0
else
    error "Usage: $0 [nobuild]"
    error "       Specify 'nobuild' to skip building Docker images."
    exit 11
fi

# VPN is required to build images and access devmail.aerofs.com during appliance setup
(set +e
    curl newci.arrowfs.org >/dev/null 2>&1
    [[ $? = 0 ]] || (
        error "ERROR: please connect to VPN"
        exit 22
    )
)

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"

if [ ${BUILD} = 1 ]; then
    info "Building protobuf & client packages for Docker images..."
    "${THIS_DIR}/../../invoke" proto build_client package_clients --mode PRIVATE --unsigned

    info "Building Docker images..."
    make -C "${THIS_DIR}/.."
fi

info "Removing AeroFS containers..."
"${THIS_DIR}/dk-destroy.sh"

info "Launching 'maintenance' container group..."
"${THIS_DIR}/crane.sh" run -dall maintenance

IP=$(docker-machine ip dev)

for PORT in {8484,80}; do
    URL="http://${IP}:${PORT}"
    info "Waiting for ${URL} readiness..."
    START=$(date +"%s")
    while true; do
        BODY="$(curl -s --connect-timeout 1 ${URL} || true)"
        [[ "${BODY}" ]] && break
        if [ $(($(date +"%s")-START)) -gt 300 ]; then
            echo "ERROR: Timeout when waiting for ${URL} readiness"
            exit 33
        fi
        sleep 1
    done
done

info "Configuring AeroFS..."
# Create and clear the reboot flag file. Can't use mktemp since docker-machine can't bind mount /tmp folders to containers
REBOOT_FLAG=${THIS_DIR}/../../out.shell/dk-create-reboot-flag
mkdir -p "$(dirname ${REBOOT_FLAG})"
echo > "${REBOOT_FLAG}"

# Run the script in the background
"${THIS_DIR}/../../system-tests/bunker/setup/test.sh" ${IP} "${REBOOT_FLAG}" false &
PID=$!

# Listen to boot flag file change. Take action once the file becomes non-empty
while true; do
    if [ "$(cat ${REBOOT_FLAG})" ]; then
        info "Launching default container group..."
        "${THIS_DIR}/crane.sh" kill -dall maintenance
        "${THIS_DIR}/crane.sh" run -dall
        info "Waiting for apply-config to finish. Use e.g. 'docker logs -f repackaging' to monitor progress..."
        break
    elif [ "$(ps -p ${PID} | sed 1d)" ]; then
        # The script is still running. Wait
        sleep 1
    else
        # The script has prematurally stopped
        exit 22
    fi
done

# Wait for the script to finish
wait ${PID}

success 'Services up and running. You may create the first user with:'
echo
success '   $ open http://$(dk-ip)'
echo
