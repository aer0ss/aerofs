#!/bin/sh

IMAGE=$1
SERVICE=$2

tee >/${GOPATH}/Dockerfile

if [ -s "${GOPATH}/Dockerfile" ] ; then
    DOCKERFILE=${GOPATH}/Dockerfile
else
    DOCKERFILE=${GOPATH}/src/${SERVICE}/Dockerfile
fi

CGO_ENABLED=0 go get -a -x -installsuffix cgo -ldflags '-s -w' ${SERVICE}
docker build -t ${IMAGE} -f ${DOCKERFILE} ${GOPATH}

