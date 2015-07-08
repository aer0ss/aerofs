#!/bin/bash
#
# The backend script for `POST /images/pull`
#
# Don't set -e. We handle errors by ourselves.
#
set -x

REPO=$1
LOADER=$2
TAG=$3
PULL_JSON=$4

write_pulling() {
    local PULLED=$1
    local TOTAL=$2
    echo "{\"status\":\"pulling\", \"pulled\":${PULLED}, \"total\":${TOTAL}}" > ${PULL_JSON}
}

write_error() {
    local MESSAGE=$1
    echo "{\"status\":\"error\", \"message\":\"${MESSAGE}\"}" > ${PULL_JSON}
}

write_done() {
    echo "{\"status\":\"done\"}" > ${PULL_JSON}
}

# Pull Loader
write_pulling 0 0
LOADER_FULL_NAME=${REPO}/${LOADER}:${TAG}
docker pull ${LOADER_FULL_NAME}
[[ $? = 0 ]] || {
    write_error "Could't pull ${LOADER_FULL_NAME}"
    exit 11
}

IMAGES="$(docker run --rm ${LOADER_FULL_NAME} images)"
[[ -n "${IMAGES}" ]] || {
    write_error "Could't list images from ${LOADER_FULL_NAME}"
    exit 22
}

# Pull application images
PULLED=0
for i in ${IMAGES}; do
    write_pulling ${PULLED} $(wc -w <<< "${IMAGES}")
    IMAGE_FULL_NAME=${REPO}/${i}:${TAG}
    docker pull ${IMAGE_FULL_NAME}
    [[ $? = 0 ]] || {
        write_error "Couldn't pull ${IMAGE_FULL_NAME}"
        exit 33
    }
    PULLED=$((PULLED+1))
done

write_done