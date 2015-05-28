#!/bin/bash
set -e

[[ $# -ge 2 ]] || {
    echo "Usage: $0 <image> <service> [<local-import> ...]"
    echo "      <image>   name of the docker image to be produced"
    echo "      <service> fully qualified name of the go package to build. e.g. 'aerofs.com/ca-server'"
    echo "      <local-import> fully qualified name of the local go package that the service depends. "
                            "e.g. 'aerofs.com/service'. The user must specify _all_ the local packages that the "
                            "service imports otherwise building would fail."
    exit 11
}
IMAGE=$1
SERVICE=$2
shift; shift
LOCAL_IMPORTS=$@

render_dockerfile() {
    for i in ${SERVICE} ${LOCAL_IMPORTS}; do
        # $'\n' is to insert newlines to env variable
        # \\ is for sed to work with newlines
        COPY_SRC="${COPY_SRC}\\"$'\n'"COPY ${i} /gopath/src/${i}"
    done

    sed -e "s@{{ copy_src }}@${COPY_SRC}@" \
        -e "s@{{ service }}@${SERVICE}@" \
        "${THIS_DIR}/Dockerfile.jinja" > "${DOCKERFILE}"

    # See color code at http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux
    echo ">>> Content of ${DOCKERFILE}:"
    echo -e "\033[0;36m"
    cat "${DOCKERFILE}"
    echo -e "\033[0m"
}

BUILDER=aerofs/golang-builder__$(sed -e 's./.__.' <<< ${SERVICE})
echo "Building ${BUILDER} ..."

# Have to place Dockerfile to the src folder otherwise `docker build` would complain.
THIS_DIR="$(dirname "$0")"
SRC_DIR="${THIS_DIR}/../../golang/src"
DOCKERFILE="${SRC_DIR}/Dockerfile.gen"
render_dockerfile

docker build -t ${BUILDER} -f "${DOCKERFILE}" "${SRC_DIR}"
rm "${DOCKERFILE}"

echo "Building container image ${IMAGE} ..."
CMD="docker build -t ${IMAGE} -f /gopath/src/${SERVICE}/Dockerfile /gopath"
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${BUILDER} ${CMD}


