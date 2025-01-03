#!/bin/bash
set -e

if [ $# != 2 ] && [ $# != 3 ]; then
    echo "Usage: $0 <path-to-ship.yml> <boot-target> <loader-container=loader>"
    exit 11
fi
SHIP_YML="$1"
TARGET="$2"
CONTAINER=${3:-loader}


echo "=============== PID $$, $(date) ==============="

# Return the value of the given key specified in ship.yml
yml() {
    grep "^$1:" "${SHIP_YML}" | sed -e "s/^$1: *//" | sed -e 's/ *$//'
}

while true; do

    # List running containers, which is the last column of 'docker ps' output
    for i in $(docker ps | awk '{print $NF}'); do
        [[ ${CONTAINER} = "${i}" ]] && {
            echo "Container '${CONTAINER}' is already running."
            docker kill ${CONTAINER} || true
        }
    done

    # Create the loader container as needed
    [[ "$(docker ps -a | awk '{print $NF}' | grep -x ${CONTAINER})" ]] || {
        echo "Creating container ${CONTAINER} ..."
        LOADER_IMAGE=$(yml 'loader')
        # Emulation doesn't allow changing of repo or tag and hence /dev/null
        docker create --name ${CONTAINER} \
            -v /var/run/docker.sock:/var/run/docker.sock \
            --entrypoint bash \
            "${LOADER_IMAGE}" \
            -c "([[ -f /target ]] || echo ${TARGET} > /target) && python -u /main.py load /dev/null /dev/null /target"
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
