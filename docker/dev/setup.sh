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

# VPN is required to access devmail during appliance setup
nc -z -G 2 devmail.aerofs.com 25 >/dev/null 2>&1 || Die 22 "ERROR: please connect to VPN for devmail.aerofs.com access"

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"

IP=$(docker-machine ip docker-dev)

"${THIS_DIR}/../../tools/wait-for-url.sh" ${IP}

info "Configuring AeroFS..."

"${THIS_DIR}/../../system-tests/bunker/setup/test.sh" ${IP} false

success 'Services up and running. You may create the first user with:'
echo
success '   open http://share.syncfs.com'
echo
