#!/bin/bash
set -e

[[ $# -ge 2 ]] && [[ $# -lt 6 ]] || {
    echo "Usage: $0 <image> <service> [<source> [<mapping> [<Dockerfile>]]]"
    echo "      <image>      name of the docker image to be produced"
    echo "      <service>    fully qualified name of the go package to build. e.g. 'aerofs.com/ca-server'"
    echo "      <source>     path to source hierarchy to be imported into container"
    echo "                   Default: ."
    echo "      <mapping>    location under which sources are imported into container, under GOPATH/src/"
    echo "                   Default: <service> or <service>/<source> if <source> starts with \"..\""
    echo "      <Dockerfile> path to out-of-tree Dockerfile. If none is provided, a Dockerfile must exist"
    echo "                   in the top-level directory of the package being built."
    exit 11
}
IMAGE=$1
SERVICE=$2
SRC_DIR=${3:-.}
if [[ $SRC_DIR == ..* ]] ; then
    DST_DIR=${4:-${SERVICE}/${SRC_DIR}}
else
    DST_DIR=${4:-${SERVICE}}
fi 
DOCKERFILE=${5:-/dev/null}

BUILDER=aerofs/golang-builder
THIS_DIR="$(dirname "$0")"

echo "Building ${BUILDER} ..."
# build base builder container
docker build -t ${BUILDER} "${THIS_DIR}"

tmpimg="${BUILDER}-$(echo "$IMAGE" | sed 's/\//./g')"
echo "Building $tmpimg ..."

# one cannot use a Dockerfile outside of the build context, hence the use of a tempfile
tmpfile="${SRC_DIR}/.golang-builder.dockerfile"

cat - > "$tmpfile" <<EOF
FROM $BUILDER
COPY . /gopath/src/$DST_DIR
EOF

# build temporary container w/ build context
docker build -t $tmpimg -f "$tmpfile" "$SRC_DIR"

rm -f "$tmpfile"

echo "Building container image ${IMAGE} ..."
docker run --rm -i -v /var/run/docker.sock:/var/run/docker.sock "$tmpimg" ${IMAGE} ${SERVICE} < ${DOCKERFILE}

# remove temporary container
docker rmi "$tmpimg"

