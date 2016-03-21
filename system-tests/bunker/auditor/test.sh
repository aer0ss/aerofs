#!/bin/bash
set -eu

[[ $# -ge 1 ]] || ( echo "Usage: $0 <ip> [<userid> [<passwd>]]"; exit 11 )

THIS_DIR="$(dirname $0)"
source "${THIS_DIR}/../../webdriver-lib/utils.sh"

info "Launching downstream server..."
IMAGE=aerofs/test.auditor-downstream
CONTAINER=aerofs-test.auditor-downstream
docker build -t ${IMAGE} "${THIS_DIR}/downstream"
docker rm -vf ${CONTAINER} 2>/dev/null || true
docker run --name ${CONTAINER} -d -P ${IMAGE}

# Find downstream's IP. TODO (WW) use docker-machine for both CI and dev environment
if [ "$(grep '^tcp://' <<< "${DOCKER_HOST:-}")" ]; then
    # Use the hostname specified in DOCKER_HOST environment variable
    DOWNSTREAM_IP=$(echo "${DOCKER_HOST}" | sed -e 's`^tcp://``' | sed -e 's`:.*$``')
else
    # Find the first IP address of the local bridge
    IFACE=$(ip route show 0.0.0.0/0 | awk '{print $5}')
    DOWNSTREAM_IP=$(ip addr show ${IFACE} | grep '^ *inet ' | head -1 | tr / ' ' | awk '{print $2}')
fi

DOWNSTREAM_PORT=$(docker port ${CONTAINER} | tr ':' ' ' | rev | awk '{print $1}' | rev)

APPLIANCE_IP="$1"
DEFAULT_USERID="admin@syncfs.com"
USERID="${2:-$DEFAULT_USERID}"
shift

# Run the tests
${THIS_DIR}/../../webdriver-lib/test-driver.sh ${THIS_DIR} "${APPLIANCE_IP}" -- ${DOWNSTREAM_IP} ${DOWNSTREAM_PORT} "$@"

info "Validating Audit stream data..."
OUTPUT="$(docker logs ${CONTAINER})"

KEY1='"event":"user.signin"'
KEY2='"event":"device.access.code"'
KEY3='"event":"device.certify"'
KEY4="\"user\":\"${USERID}\""

(
    [[ "$(grep "${KEY1}" <<< "${OUTPUT}")" ]] &&
    [[ "$(grep "${KEY2}" <<< "${OUTPUT}")" ]] &&
    [[ "$(grep "${KEY3}" <<< "${OUTPUT}")" ]] &&
    [[ "$(grep "${KEY4}" <<< "${OUTPUT}")" ]]
) || {
    error "FAILED: Audit stream contains invalid data. Stream content as follows:"
    echo
    echo "==== STREAM BEGIN ===="
    echo "${OUTPUT}"
    echo "==== STREAM END ===="
    exit 22
}

# Kill downstream server
docker rm -vf ${CONTAINER}
