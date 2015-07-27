#!/bin/bash
set -e

[[ $# = 1 ]] || {
    echo "Usage: $0 <ip>"
    exit 11
}
IP=$1

# Constants
CONNECT_TIMEOUT=3
WAIT_TIMEOUT=300
REPO=registry.hub.docker.com

BOOT_ID=0

wait_for_boot_id_change() {
    local URL="http://${IP}/v1/boot"
    echo ">>> Waiting for boot ID change ..."
    START=$(date +"%s")
    while true; do
        rm -f /tmp/body
        STATUS="$(curl -s --output /tmp/body --write-out "%{http_code}" --connect-timeout ${CONNECT_TIMEOUT} ${URL} || true)"
        # /tmp/body might not exist
        BODY="$(cat /tmp/body 2>/dev/null || true)"

        # Wait until the URL returns 200 with a different boot number than before.
        [[ "${STATUS}" = 200 ]] && [[ "${BODY}" ]] && [[ "$(jq -r .id <<< "${BODY}")" != "${BOOT_ID}" ]] && {
            BOOT_ID="$(jq -r .id <<< "${BODY}")"
            break
        }

        if [ $(($(date +"%s")-START)) -gt ${WAIT_TIMEOUT} ]; then
            echo "ERROR: Timeout for ${URL} to return 200"
            exit 11
        fi
        sleep 1
    done
}

pull() {
    local TAG=$1
    echo ">>> Pulling version '${TAG}' ..."
    curl -sS -XPOST http://${IP}/v1/images/pull/${REPO}/${TAG} > /dev/null
    while true; do
        local OUTPUT="$(curl -sS http://${IP}/v1/images/pull)"
        local STATUS="$(jq -r .status <<< "${OUTPUT}")"
        [[ "${STATUS}" = done ]] && break
        [[ "${STATUS}" = pulling ]] || {
            echo "ERROR: pulling failed: ${OUTPUT}"
            exit 22
        }
        sleep 1
    done
}

switch_to() {
    local TAG=$1
    echo ">>> Switching to version '${TAG}' ..."
    curl -sS -XPOST http://${IP}/v1/switch/${REPO}/${TAG}/default || true
    wait_for_boot_id_change
}

read_target_file() {
    curl -sS http://${IP}/data2/file
}

echo ">>> Waiting to boot the app to version 'past' ..."
wait_for_boot_id_change

# Verify the target file isn't there
MAGIC_STRING="magic string"
[[ "$(read_target_file | grep "${MAGIC_STRING}")" ]] && {
    echo "ERROR: the target file shouldn't exist yet"
    exit 33
}

# Upgrade from 'past' to 'present'
pull present
switch_to present

# Upgrade from 'present' to 'future'
pull future
switch_to future

echo ">>> Verifying target file ..."
[[ "$(read_target_file | grep "${MAGIC_STRING}")" ]] || {
    echo "ERROR: the target file doesn't exist or its content doesn't match the magic string."
    exit 44
}
