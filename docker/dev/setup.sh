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

DEVMAIL=devmail.aerofs.com
echo "Testing connection to ${DEVMAIL} ..."
# VPN is required to access devmail during appliance setup
nc -z ${DEVMAIL} 25 || Die 22 "ERROR: please connect to VPN for ${DEVMAIL} access"

# Find the appliance's IP
if [ "$(grep '^tcp://' <<< "${DOCKER_HOST}")" ]; then
    # Use the docker daemon's IP
    IP=$(echo "${DOCKER_HOST}" | sed -e 's`^tcp://``' | sed -e 's`:.*$``')
else
    # Assuming the script runs in the CI agent container, and the appliance containers run on the same host with
    # --net=host. The appliance IP is then the agent container's own IP.
    IP=$(ip route show 0.0.0.0/0 | awk '{print $9}')
fi

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"
"${THIS_DIR}/../../tools/wait-for-url.sh" ${IP}

info "Configuring AeroFS..."

"${THIS_DIR}/../../system-tests/bunker/setup/test.sh" ${IP} false

success 'Services up and running. You may create the first user with:'
echo
success '   open http://share.syncfs.com'
echo
