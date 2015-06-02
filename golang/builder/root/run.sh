#!/bin/sh

IMAGE=$1
SERVICE=$2

CGO_ENABLED=0 go get -a -x -installsuffix cgo -ldflags '-s -w' ${SERVICE}
docker build -t ${IMAGE} -f ${GOPATH}/src/${SERVICE}/Dockerfile ${GOPATH}

