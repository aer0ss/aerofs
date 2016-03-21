#!/bin/bash
set -eu

if [ $# != 2 ]; then
    echo "Usage: $0 <loader-image> <boot target>"
    exit 1
fi
LOADER=$1
TARGET=$2

THIS_DIR="$(dirname $0)"

SHIP_YML="$("${THIS_DIR}/../ship-aerofs/render-ship-yml.sh" $LOADER)"

abspath() {
    (cd "$1" && pwd)
}

DIR="${THIS_DIR}/../../out.ship"
mkdir -p "${DIR}"
LOG="$(abspath "${DIR}")/emulate.log"

CYAN='0;36'
cecho() {
    col=$1 ; shift
    echo -e "\033[${col}m${@}\033[0m";
}

cecho ${CYAN} "Running Ship Emulator in the background..."
echo "See ${LOG} for logs."

LOADER_CONTAINER=$(basename $LOADER)
nohup "${THIS_DIR}/../ship/emulate.sh" "${SHIP_YML}" $TARGET $LOADER_CONTAINER >>"${LOG}" 2>&1 &
disown
