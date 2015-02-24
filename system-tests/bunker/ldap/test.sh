#!/bin/bash
set -e

[[ $# = 1 ]] || ( echo "Usage: $0 <ip>"; exit 11 )

function test_port() {
    # See http://bit.ly/1vDblqg
    exec 3<> "/dev/tcp/$1/$2"
    CODE=$?
    exec 3>&- # close output
    exec 3<&- # close input
    echo ${CODE}
}

function wait_port() {
    echo "Waiting for $1:$2 readiness..."
    while [ $(test_port $1 $2 2> /dev/null) != 0 ]; do
    	sleep 1
    done
}

THIS_DIR="$(dirname $0)"

# Launch OpenDS server
IMAGE=aerofs/test.opends
CONTAINER=aerofs-test.opends
docker build -t ${IMAGE} "${THIS_DIR}/opends"
docker rm -vf ${CONTAINER} 2>/dev/null || true
docker run --name ${CONTAINER} -d -p 389:389 aerofs/test.opends

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

# Run the tests
${THIS_DIR}/../../webdriver-lib/test-wrapper.sh ${THIS_DIR} "$1" -- ${OPENDS_IP} || true

# Kill OpenDS server
docker rm -vf ${CONTAINER}