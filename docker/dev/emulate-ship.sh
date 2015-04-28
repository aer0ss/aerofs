#!/bin/bash
set -e

THIS_DIR="$(dirname $0)"

SHIP_YML="$("${THIS_DIR}/../ship-aerofs/render-ship-yml.sh")"

abspath() {
    (cd "$1" && pwd)
}

LOG="$(abspath ${THIS_DIR}/../../out.ship)/emulate.log"

CYAN='0;36'
cecho() {
    col=$1 ; shift
    echo -e "\033[${col}m${@}\033[0m";
}

cecho ${CYAN} "Running Ship Emulator in the background..."
echo "See ${LOG} for logs."

nohup "${THIS_DIR}/../ship/emulate.sh" "${SHIP_YML}" $@ >>"${LOG}" 2>&1 &

disown
