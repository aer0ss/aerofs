#!/bin/bash
#
# This script sequentially runs all test.sh found in the current folder and subfolders.
# Folders in the IGNORE list are ignored. The IP of the appliance is passed to all test.sh
# as the only parameter.
set -e

IGNORE="
    syncdet
    bunker/setup
"

[[ $# = 1 ]] || { echo "Usage: $0 <appliance-ip>"; exit 11; }
IP="$1"

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"

cyan_echo() { echo -e "\033[0;36m$1\033[0m"; }

for i in $(find "${THIS_DIR}" -name test.sh); do
    RELATIVE_DIR="$(sed -e "s|^${THIS_DIR}/||" <<< "$(dirname "${i}")")"
    if [ -z "$(grep "${RELATIVE_DIR}" <<< "${IGNORE}")" ]; then
        cyan_echo "============================================================"
        cyan_echo " Running test ${RELATIVE_DIR}..."
        cyan_echo "============================================================"
        "${i}" "${IP}"
    fi
done
