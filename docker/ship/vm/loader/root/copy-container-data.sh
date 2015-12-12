#!/bin/bash
set -ex

LOADER_IMAGE=$1
SOURCE=$2
TARGET=$3
shift 3
VOLUMES="$@"

HOST_FOLDER=/data-buffer
# '-xxx' to avoid potential name conflicts with applicaiton folders
BIND_FOLDER=/data-xxx

# Copy data from source container to host.
docker run --rm -v ${HOST_FOLDER}:${BIND_FOLDER} -v /var/run/docker.sock:/var/run/docker.sock \
    --volumes-from ${SOURCE} --entrypoint bash ${LOADER_IMAGE} \
    -cex "rm -rf ${BIND_FOLDER}/*; for i in ${VOLUMES}; do PARENT=${BIND_FOLDER}\$(dirname \$i); mkdir -p \$PARENT; cp -a \$i \$PARENT; done"

# Copy data from host to to target container
docker run --rm -v ${HOST_FOLDER}:${BIND_FOLDER} -v /var/run/docker.sock:/var/run/docker.sock \
    --volumes-from ${TARGET} --entrypoint bash ${LOADER_IMAGE} \
    -cex "cp -a ${BIND_FOLDER}/* /; rm -rf ${BIND_FOLDER}/*"
