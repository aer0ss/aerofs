#!/bin/bash
set -e

# See http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux for color code
GREEN='0;32'
CYAN='0;36'
YELLOW='1;33'
RED='0;31'
cecho() {
    col=$1 ; shift
    echo -e "\033[${col}m${@}\033[0m"; }
info() { cecho ${CYAN} "$@"; }
success() { cecho ${GREEN} "$@"; }
error() { cecho ${RED} "$@"; }
Die() {
    retval=$1 ; shift
    error "$@";
    exit ${retval}
}

# VPN is required to access devmail.aerofs.com during appliance setup
curl newci.arrowfs.org >/dev/null 2>&1 || Die 22 "ERROR: please connect to VPN"

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"

info "Removing AeroFS containers..."
"${THIS_DIR}/dk-destroy.sh"

info "Launching 'maintenance' container group..."
"${THIS_DIR}/dk-crane.sh" run -dall maintenance

IP=$(docker-machine ip docker-dev)

URL="http://${IP}"
info "Waiting for ${URL} readiness..."
START=$(date +"%s")
while true; do
    BODY="$(curl -s --connect-timeout 1 ${URL} || true)"
    [[ "${BODY}" ]] && break
    if [ $(($(date +"%s")-START)) -gt 300 ]; then
        Die 33 "ERROR: Timeout when waiting for ${URL} readiness"
    fi
    sleep 1
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
        "${THIS_DIR}/dk-crane.sh" kill -dall maintenance
        "${THIS_DIR}/dk-crane.sh" run -dall
        info "Waiting for apply-config to finish. Use e.g. 'docker logs -f repackaging' to monitor progress..."
        break
    elif [ "$(ps -p ${PID} | sed 1d)" ]; then
        # The script is still running. Wait
        sleep 1
    else
        # The script has prematurely stopped
        Die 22 "Something unexpected happened."
    fi
done

# Wait for the script to finish
wait ${PID}

success 'Services up and running. You may create the first user with:'
echo
success '   open http://share.syncfs.com'
echo
