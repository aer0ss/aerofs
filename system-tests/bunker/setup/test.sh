#!/bin/bash
set -e
#
# Precondition: the appliance is running at the specified hostname and hasn't been set up yet.
#
if [ $# -lt 2 ]; then
    echo "Usage: $0 <host> <create-first-user> [extra-docker-args]"
    echo "       <host> hostname of the appliance under test"
    echo "       <create-first-user> 'true' to create the first admin user. It requires the Sign-up Decoder service"
    echo "                      (see ci-cloud-config.jinja)."
    echo "       [extra-docker-args] passed directly to the docker container"
    exit 11
fi
HOST="$1"; shift
CREATE_FIRST_USER="$1"; shift
while [ $# != 0 ]; do
    EXTRA_DOCKER_ARGS="${EXTRA_DOCKER_ARGS} $1"
    shift
done

# Docker requires absolute paths. OSX has no realpath command.
THIS_DIR="$(cd $(dirname "$0") && pwd)"

${THIS_DIR}/../../webdriver-lib/test-driver.sh ${THIS_DIR} ${HOST} ${EXTRA_DOCKER_ARGS} -- ${CREATE_FIRST_USER}
