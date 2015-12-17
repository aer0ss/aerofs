#!/bin/bash
set -e
#
# removes the containers, their volume data, and container images that don't belong to the current version.
#

LOADER_IMAGE=$1
CUR_TAG=$2
GC_JSON=$3
BOOT_ID=$4


clean() {
    local REPO="$1"
    local TAG=$2
    # FQIN: fully qualified image name
    local LOADER_FQIN=${REPO}/${LOADER_IMAGE}:${TAG}
    echo "Cleaning ${LOADER_FQIN} ..."
    local CMD_PREFIX="docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_FQIN}"
    for CONTAINER in $(${CMD_PREFIX} modified-containers "${REPO}" ${TAG}); do
        (set -x; docker rm -vf ${CONTAINER} || true)
    done

    # Remove the loader last in case this script fails before removing all images
    for IMAGE in $(${CMD_PREFIX} modified-images "${REPO}" ${TAG} | grep -v ${LOADER_FQIN}); do
        (set -x; docker rmi -f ${IMAGE} || true)
    done
    (set -x; docker rmi -f ${LOADER_FQIN})
}

write_clean_progress() {
    echo "{\"status\":\"cleaning\", \"bootid\":\"${BOOT_ID}\"}" > ${GC_JSON}
}

write_done() {
    echo "{\"status\":\"done\"}" > ${GC_JSON}
}

main() {
    # Find all the loaders that aren't at the current version. Note that they may have different repo names than the
    # current loader.
    #
    # First, list image names ending with $LOADER_IMAGE. `sort` & `uniq` to dedup identical image names with diff tags.
    write_clean_progress
    local IMAGES=$(docker images | awk '{print $1}' | grep -e "/${LOADER_IMAGE}$" | sort | uniq)
    CLEANED=0

    for IMAGE in ${IMAGES}; do
        local REPO=$(sed -e "s./${LOADER_IMAGE}$.." <<< ${IMAGE})
        # Second, list image tags. `tail` to skip header
        for TAG in $(docker images ${IMAGE} | tail -n +2 | awk '{print $2}'); do
            [[ ${TAG} = ${CUR_TAG} ]] || clean "${REPO}" ${TAG}
        done
        CLEANED=$((CLEANED+1))
    done
    write_done
}

main
