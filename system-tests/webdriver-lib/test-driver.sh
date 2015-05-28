#!/bin/bash
set -e
#
# This script simplifies writing Web Driver tests for the AeroFS appliance. It builds Docker images from the specified
# directory, run the container with appropriate arguments, and print pretty console output. It assumes the container is
# based on aerofs/base.webdriver, and uses /main.py as the Python entry point. See the "docker run" invocation below for
# exact parameters and volume data being passed to the container.
#

if [ $# -lt 2 ]; then
    echo "Usage: $0 <caller-dir> <ip> [<extra-docker-args> [-- <extra-python-args>]]"
    echo "       <caller-dir> the path where the test code resides. A Dockerfile is assumed present in this path."
    echo "       <extra-docker-args> additional arguments to pass to the docker command."
    echo "       <extra-python-args> additional arguments to pass to main.py."
    exit 11
fi

# Parse args
CALLER_DIR="$1"; shift
IP="$1"; shift
while [ $# != 0 ] && [ "$1" != -- ]; do
    EXTRA_DOCKER_ARGS="${EXTRA_DOCKER_ARGS} $1"
    shift
done
if [ $# != 0 ]; then
    shift # shift off '--'
    EXTRA_PYTHON_ARGS=$@
fi

# Docker requires absolute paths. OSX has no realpath command.
THIS_DIR="$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)"
BASE_DIR="$(dirname "${THIS_DIR}")"
CALLER_DIR="$(cd "${CALLER_DIR}" && pwd)"
if [ -z "$(grep "^${BASE_DIR}" <<< "${CALLER_DIR}")" ]; then
    error "ERROR: the caller script must be in a subfolder under ${BASE_DIR}."
    exit 22
fi

source "${THIS_DIR}/utils.sh"

# Derive image and container names from the caller dir.
RELATIVE_CALLER_DIR="$(sed -e "s@^${BASE_DIR}/@@" <<< "${CALLER_DIR}")"
IMAGE_NAME="aerofs/test.$(tr '/' '-' <<< "${RELATIVE_CALLER_DIR}")"
CONTAINER_NAME="aerofs-test.$(tr '/' '-' <<< "${RELATIVE_CALLER_DIR}")"

info "Building image aerofs/base.webdriver ..."
docker build -t aerofs/base.webdriver "${THIS_DIR}"

info "Building image ${IMAGE_NAME} ..."
docker build -t "${IMAGE_NAME}" "${CALLER_DIR}"

info "Running container ${CONTAINER_NAME} ..."
# redirect stderr to suppress the big red error if the container is not running
docker rm -fv "${CONTAINER_NAME}" 2> /dev/null || true

SCREEN_SHOTS="${THIS_DIR}/../../out.shell/screenshots/${RELATIVE_CALLER_DIR}"
mkdir -p "${SCREEN_SHOTS}"
rm -rf "${SCREEN_SHOTS}"/*
# Get canonical path for prettier screen output
SCREEN_SHOTS="$(cd "${SCREEN_SHOTS}" && pwd)"

HOST=share.syncfs.com

(set +e
    (set -x; docker run --rm --name "${CONTAINER_NAME}" \
        --add-host "${HOST}:${IP}" \
        -v "${SCREEN_SHOTS}":/screenshots \
        ${EXTRA_DOCKER_ARGS} \
        "${IMAGE_NAME}" python -u main.py ${HOST} ${EXTRA_PYTHON_ARGS})

    EXIT_CODE=$?

    MSG="Screenshots at ${SCREEN_SHOTS}"
    echo
    if [ ${EXIT_CODE} = 0 ]; then
        success ">>> PASSED. ${MSG}"
    else
        error ">>> FAILED: $(basename "${BASE_DIR}")/${RELATIVE_CALLER_DIR}. ${MSG}"
    fi
    echo

    exit ${EXIT_CODE}
)
