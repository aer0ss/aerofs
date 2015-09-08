#!/bin/bash
set -eu
# devpi provides package caching for pypi

PWD="$(cd $(dirname $0); pwd -P)"

success() { echo >&2 -e "\033[32mok: \033[0m- start devpi"; }

if [[ -n "$(docker ps -q -f 'name=devpi')" ]] ; then
    echo "devpi server already running"
    success
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
docker run -d --restart=always --name devpi \
    --volumes-from cache-pypi \
    devpi
success
