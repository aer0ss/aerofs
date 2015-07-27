#!/bin/bash
set -ex

[[ $# = 1 ]] || {
    echo "Usage: $0 <docker-hub-user-name>"
    exit 11
}
DOCKER_USER=$1
TAGS="past present future"

THIS_DIR="$(dirname "$0")"
IMAGE_PREFIX=${DOCKER_USER}/shipenterprise.test-

build_loaders() {
    "${THIS_DIR}"/../../vm/loader/build.sh
    for TAG in ${TAGS}; do build_loader ${TAG}; done
}

build_loader() {
    local TAG=$1
    local IMAGE=${IMAGE_PREFIX}loader
    local BUILD_DIR="${THIS_DIR}/loader/build"

    # Generate files
    mkdir -p "${BUILD_DIR}"
    sed -e "s~{{ image_prefix }}~${IMAGE_PREFIX}~" "${THIS_DIR}/loader/crane.yml.jinja" > "${BUILD_DIR}/crane.yml"
    cat > "${BUILD_DIR}/Dockerfile" <<END
        FROM shipenterprise/vm-loader
        COPY crane.yml /crane.yml
        RUN echo ${TAG} > /tag
END

    # Build and push
    docker build -t ${IMAGE}:${TAG} "${BUILD_DIR}"
    docker run --rm ${IMAGE}:${TAG} verify ${IMAGE}
    docker push ${IMAGE}:${TAG}
}

tag_latest_loader() {
    local TAG=$1
    docker tag -f ${IMAGE_PREFIX}loader:${TAG} ${IMAGE_PREFIX}loader
    docker push ${IMAGE_PREFIX}loader
}

build_nginx() {
    local IMAGE=${IMAGE_PREFIX}nginx
    docker build -t ${IMAGE} "${THIS_DIR}/nginx"
    for TAG in ${TAGS}; do
        docker tag -f ${IMAGE} ${IMAGE}:${TAG}
        docker push ${IMAGE}:${TAG}
    done
}

build_data() {
    local IMAGE=${IMAGE_PREFIX}data
    for TAG in ${TAGS}; do
        docker build -t ${IMAGE}:${TAG} -f "${THIS_DIR}/data/Dockerfile.${TAG}" "${THIS_DIR}/data"
        docker push ${IMAGE}:${TAG}
    done
}

build_cloud_config() {
    local BUILD_DIR="${THIS_DIR}/build"
    mkdir -p "${BUILD_DIR}"
    sed -e "s~{{ image_prefix }}~${IMAGE_PREFIX}~" "${THIS_DIR}/ship.yml.jinja" > "${BUILD_DIR}/ship.yml"
    "${THIS_DIR}/../../vm/build.sh" cloudinit "${BUILD_DIR}/ship.yml" "" "${BUILD_DIR}/ship"
}

build_cloud_config
build_nginx
build_data
build_loaders

# Use 'past' as the latest version
tag_latest_loader past
