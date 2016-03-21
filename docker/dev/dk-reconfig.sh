#!/bin/bash
set -eu

# See http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux for color code
GREEN='0;32'
CYAN='0;36'
YELLOW='1;33'
RED='0;31'
cecho() {
    col=$1 ; shift
    echo -e "\033[${col}m${@}\033[0m";
}
info() { cecho ${CYAN} "$@"; }
success() { cecho ${GREEN} "$@"; }
error() { cecho ${RED} "$@"; }

[[ $# = 1 || $# = 2 ]] || {
    error "usage: $0 create-first-user|no-create-first-user [hostname]"
    exit 11
}
if [[ $1 = create-first-user ]]; then
    CREATE_FIRST_USER=true
elif [[ $1 = no-create-first-user ]] ; then
    CREATE_FIRST_USER=false
else
    error "usage: $0 create-first-user|no-create-first-user [hostname]"
    exit 11
fi

HOST=${2:-share.syncfs.com}

DEVMAIL=devmail.aerofs.com
echo "Testing connection to ${DEVMAIL} ..."
# VPN is required to access devmail during appliance setup
nc -z ${DEVMAIL} 25 || {
    error "ERROR: please connect to VPN for ${DEVMAIL} access"
    exit 22
}

THIS_DIR="$(dirname "$0")"

"${THIS_DIR}"/dk-destroy.sh

"${THIS_DIR}"/signup-decoder/stop.sh

"${THIS_DIR}"/signup-decoder/start.sh

"${THIS_DIR}"/../ci/modify-appliance.sh

"${THIS_DIR}"/emulate-ship.sh aerofs/loader maintenance

# Find the appliance's IP
if [ "$(grep '^tcp://' <<< "${DOCKER_HOST:-}")" ]; then
    # Use the docker daemon's IP
    IP=$(echo "${DOCKER_HOST}" | sed -e 's`^tcp://``' | sed -e 's`:.*$``')
else
    # Assuming the script runs in the CI agent container, and the appliance containers run on the same host.
    # The appliance IP is then the host's bridge IP.
    IP=$(ip route show 0.0.0.0/0 | awk '{print $3}')
fi

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"
"${THIS_DIR}/../../tools/wait-for-url.sh" ${HOST}

"${THIS_DIR}/../../system-tests/bunker/setup/test.sh" ${HOST} ${CREATE_FIRST_USER} ${EXTRA_DOCKER_ARGS:-}

if [ ${CREATE_FIRST_USER} = true ]; then
    success 'Services is up and running.'
else
    success 'Services is up and running. You may create the first user with:'
    echo
    success "   open http://$HOST"
fi
echo
