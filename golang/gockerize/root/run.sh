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
    pushd /usr/local/go/ >>/dev/null
    for p in ${GOPATH}/src/${SERVICE}/patches/*.patch ; do
        patch -p1 < $p
    done
    popd >>/dev/null
fi

CGO_ENABLED=0 GO15VENDOREXPERIMENT=1 go get $GOARGS -a -installsuffix cgo -ldflags '-d -s -w' ${SERVICE}
docker build --no-cache -t ${IMAGE} -f ${DOCKERFILE} ${GOPATH}
