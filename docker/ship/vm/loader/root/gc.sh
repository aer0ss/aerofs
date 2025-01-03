#!/bin/bash
#
# removes the containers, their volume data, and container images that don't belong to the current version.
# N.B.: For local dk appliance, current version is always what is built locally even when pulling registry images
# via IPU. So calling this script will result in cleaning up what registry images were pulled in during IPU.
# TODO(AS):
# 1. This script is way too fancy and complicated. Please rethink and move to python at your
# earliest convenience.
# 2. This script used to contain set -e to exit immediately in case of any error including when trying
# to remove a not found/not deletable container/image. I removed it because its hard to say with
# certainity what weird artificats remain/are deleted from previous(and possibly busted upgrades). The script
# should clean almost all old containers and images. If it fails to clean a single one or two, it shouldn't
# be treated as a fatal error because the impact is not great and it should't stop from cleaning others.

LOADER_IMG_NAME=$1
CUR_TAG=$2
GC_JSON=$3
BOOT_ID=$4


clean() {
    local REPO="$1"
    local TAG=$2
    # FQIN: fully qualified image name
    local LOADER_FQIN=${REPO}/${LOADER_IMG_NAME}:${TAG}
    echo "Cleaning ${LOADER_FQIN} ..."
    local CMD_PREFIX="docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${LOADER_FQIN}"
    local CONTAINERS="${CMD_PREFIX} modified-containers "${REPO}" ${TAG}"
    local IMAGES="${CMD_PREFIX} modified-images "${REPO}" ${TAG} | grep -v ${LOADER_FQIN}"

    for CONTAINER in $(eval $CONTAINERS); do
        (set -x; docker rm -vf ${CONTAINER} || echo "Failed to remove container: $CONTAINER")
    done

    # Remove the loader last in case this script fails before removing all images
    for IMAGE in $(eval $IMAGES); do
        (set -x; docker rmi -f ${IMAGE} || echo "Failed to remove image: $IMAGE")
    done
    (set -x; docker rm -vf "loader-${TAG}" || echo "Failed to remove container: loader-$TAG")
    (set -x; docker rmi -f ${LOADER_FQIN} || echo "Failed to remove image: $LOADER_FQIN")
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
    # First, list image names ending with $LOADER_IMG_NAME. `sort` & `uniq` to dedup identical image names with diff tags.
    write_clean_progress
    local IMAGES=$(docker images | awk '{print $1}' | grep -e "${LOADER_IMG_NAME}$" | sort | uniq)
    # ONLY in docker-dev, the loader's API maintains the tag as ""(empty string) and that is what is passed in
    # to this script. When building images locally passing in a empty string for tag resolves to "latest"
    if [[ "$CUR_TAG" == "" ]]; then
        CUR_TAG="latest"
    fi
    local LATEST_IMAGE_ID=$(docker images | grep -e "${LOADER_IMG_NAME}" | grep $CUR_TAG | awk '{print $3}')

    for IMAGE in ${IMAGES}; do
        local REPO=$(sed -n 's/\(.*\)\/aerofs\/loader$/\1/p' <<< ${IMAGE})
        OLDIFS=$IFS
        IFS=$'\n'
        # Second, list image tags. `tail` to skip header
        for LOADER_IMG in $(docker images ${IMAGE} | tail -n +2); do
            local TAG=$(echo $LOADER_IMG | awk '{print $2}')
            local ID=$(echo $LOADER_IMG | awk '{print $3}')
            # Only clean if loader image doesn't have latest tag number or
            # if its image id is not same as image id of latest loader image.
            [[ ${TAG} = ${CUR_TAG} ]] \
                || [[ ${LATEST_IMAGE_ID} = $ID ]] \
                || clean "${REPO}" ${TAG}
        done
        IFS=$OLDIFS
    done
    # Kill all dangling images. Do it explicitly because unless dangling images might not
    # be cleaned by the previous loop.
    for DANGLING in $(docker images -q -f dangling=true); do
       (set -x; docker rmi -f ${DANGLING} || echo "Failed to dangling image: $DANGLING")
    done
    write_done
}

main
