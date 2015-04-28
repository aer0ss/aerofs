#!/bin/bash
set -e

if [ $# != 2 ]; then
    echo "Usage: $0 <path-to-ship.yml> <boot-target>"
    exit 11
fi
SHIP_YML="$1"
TARGET="$2"

echo "=============== PID $$, $(date) ==============="

# Return the value of the given key specified in ship.yml
yml() {
    grep "^$1:" "${SHIP_YML}" | sed -e "s/^$1: *//" | sed -e 's/ *$//'
}

LOADER_IMAGE=$(yml 'loader')
# The folder needs to be under HOME otherwise boot2docker cannot bing mount it.
DIR="${HOME}/.ship/$(sed -e 's!/.*!!' <<< "${LOADER_IMAGE}")"
TARGET_FILE="${DIR}/target"

mkdir -p "${DIR}"
echo "${TARGET}" > "${TARGET_FILE}"

while true; do
    CONTAINER=loader

    # List running containers, which is the last column of 'docker ps' output
    for i in $(docker ps | rev | awk '{print $1}' | rev); do
        [[ ${CONTAINER} = "${i}" ]] && {
            echo "Container '${CONTAINER}' is already running."
            echo "PID $$ exits with code 0."
            exit 0
        }
    done

    [[ "$(docker ps -a | grep ${CONTAINER})" ]] || {
        echo "Creating container ${CONTAINER}..."
        # Emulation doesn't allow changing of repo or tag
        docker create --name ${CONTAINER} \
            -v /var/run/docker.sock:/var/run/docker.sock \
            -v "${TARGET_FILE}":/target \
            "${LOADER_IMAGE}" load /dev/null /dev/null /target
    }
    echo "Starting container ${CONTAINER}..."

    set +e
    docker start -a ${CONTAINER}
    EXIT=$?
    echo "Container ${CONTAINER} exited with code ${EXIT}."
    set -e

    [[ ${EXIT} = 0 ]] || {
        echo "PID $$ exits with code ${EXIT}."
        exit ${EXIT}
    }

    echo "Restarting container ${CONTAINER}..."
done