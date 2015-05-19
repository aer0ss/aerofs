#!/bin/sh

IMAGE_NAME=$1
SERVICE_NAME=$2

CGO_ENABLED=0 go get -a -x -installsuffix cgo -ldflags '-s -w' $SERVICE_NAME
docker build -t $IMAGE_NAME -f $GOPATH/src/$SERVICE_NAME/Dockerfile $GOPATH

