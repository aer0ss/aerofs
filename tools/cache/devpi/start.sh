#!/bin/bash
# devpi provides package caching for pypi

set -e

PWD="$( cd $(dirname $0) ; pwd -P )"

if [[ -n "$(docker ps -q -f 'name=devpi')" ]] ; then
    echo "devpi server already running"
    exit 0
fi

docker build -t devpi $PWD

if [[ -n "$(docker ps -aq -f 'name=devpi')" ]] ; then
    echo "removing stopped devpi server"
    docker rm --force devpi
fi

if [[ -z "$(docker ps -aq -f 'name=cache-pypi')" ]] ; then
    echo "creating pypi cache volume"
    docker create -v /var/cache/devpi --name cache-pypi alpine:3.3 /bin/true
fi

echo "starting devpi server"
docker run -d --restart=always --name devpi --volumes-from cache-pypi devpi

