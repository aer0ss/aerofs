#!/bin/bash
set -e

[[ $# = 1 ]] || ( echo "Usage: $0 <ip>"; exit 11 )

THIS_DIR="$(dirname $0)"
source "${THIS_DIR}/../../webdriver-lib/utils.sh"

info "Launching OpenDS server..."
IMAGE=aerofs/test.opends
CONTAINER=aerofs-test.opends
docker build -t ${IMAGE} "${THIS_DIR}/opends"
docker rm -vf ${CONTAINER} 2>/dev/null || true
docker run --name ${CONTAINER} -d -p 389:389 -p 636:636 ${IMAGE}

# Find OpenDS's IP. TODO (WW) use docker-machine for both CI and dev environment
if [ "$(grep '^tcp://' <<< "${DOCKER_HOST}")" ]; then
    # Use the hostname specified in DOCKER_HOST environment variable
    OPENDS_IP=$(echo "${DOCKER_HOST}" | sed -e 's`^tcp://``' | sed -e 's`:.*$``')
else
    # Find the first IP address of the local bridge
    IFACE=$(ip route show 0.0.0.0/0 | awk '{print $5}')
    OPENDS_IP=$(ip addr show ${IFACE} | grep '^ *inet ' | head -1 | tr / ' ' | awk '{print $2}')
fi

wait_port ${OPENDS_IP} 389

# Find OpenDS's server cert
docker cp aerofs-test.opends:/server-cert.pem ${THIS_DIR}/root 

OPENDS_SERVER_CERT="server-cert.pem"

# Run the tests
${THIS_DIR}/../../webdriver-lib/test-driver.sh ${THIS_DIR} "$1" -- ${OPENDS_IP} ${OPENDS_SERVER_CERT} 

# Kill OpenDS server
docker rm -vf ${CONTAINER}
