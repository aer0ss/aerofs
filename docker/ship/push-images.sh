#!/bin/bash
set -e

if [ $# != 1 ]; then
    echo "Usage: $0 <path_to_ship.yml>"
    exit 11
fi

SHIP_YML="$1"

# Return the value of the given key specified in ship.yml
yml() {
    grep "^$1:" "${SHIP_YML}" | sed -e "s/^$1: *//" | sed -e 's/ *$//'
}

LOADER_IMAGE=$(yml 'loader')
TAG=$(docker run --rm ${LOADER_IMAGE} tag)

PUSH_REPO=$(yml 'push-repo')
if [ -z "${PUSH_REPO}" ]; then
    PUSH_REPO=$(yml 'repo')
fi

# Retry 'docker push $0' with exponential backoff. Needed as some docker registries are not reliably reachable.
push() {
    (set +e
        RETRY=0
        TIMEOUT=1
        while true; do
            docker push $1
            if [ $? = 0 ]; then
                break
            elif [ ${RETRY} = 6 ]; then
                echo "ERROR: Retried too many times. I gave up."
                exit 22
            else
                echo "Retry #${RETRY} in ${TIMEOUT} seconds..."
                sleep ${TIMEOUT}
                TIMEOUT=$[TIMEOUT * 2]
                RETRY=$[RETRY + 1]
            fi
        done
    )
}

for i in $(docker run --rm ${LOADER_IMAGE} images); do
    echo "============================================================"
    echo " Pushing ${i}:${TAG} to ${PUSH_REPO}..."
    echo "============================================================"
    PUSH_IMAGE="${PUSH_REPO}/${i}:${TAG}"
    docker tag -f "${i}" "${PUSH_IMAGE}"
    push "${PUSH_IMAGE}"
    docker rmi "${PUSH_IMAGE}"
done

echo "============================================================"
echo " Pushing ${LOADER_IMAGE}:latest to ${PUSH_REPO}..."
echo "============================================================"
LOADER_PUSH_IMAGE=${PUSH_REPO}/${LOADER_IMAGE}
docker tag -f ${LOADER_IMAGE} ${LOADER_PUSH_IMAGE}
push ${LOADER_PUSH_IMAGE}
docker rmi ${LOADER_PUSH_IMAGE}


echo
echo ">>> New version successfully released: ${TAG}"
echo

