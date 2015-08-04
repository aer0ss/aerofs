#!/bin/bash

set -e

IMAGE=$1
SERVICE=$2

tee >/${GOPATH}/Dockerfile

if [ -s "${GOPATH}/Dockerfile" ] ; then
    DOCKERFILE=${GOPATH}/Dockerfile
else
    DOCKERFILE=${GOPATH}/src/${SERVICE}/Dockerfile
fi

# apply optional stdlib patches
if [ -d "${GOPATH}/src/${SERVICE}/patches" ] ; then
    pushd /usr/share/go/
    for p in ${GOPATH}/src/${SERVICE}/patches/*.patch ; do
        patch -p1 < $p
    done
    popd
fi

CGO_ENABLED=0 go get -a -x -installsuffix cgo -ldflags '-d -s -w' ${SERVICE}
docker build -t ${IMAGE} -f ${DOCKERFILE} ${GOPATH}

