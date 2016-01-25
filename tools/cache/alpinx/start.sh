#!/bin/bash
# alpinx is a poor man's package cacher for Alpine Linux
# it's basically an nginx instance configured as a caching reverse-proxy

set -e

PWD="$( cd $(dirname $0) ; pwd -P )"

if [[ -n "$(docker ps -q -f 'name=alpinx')" ]] ; then
    echo "devpi server already running"
    exit 0
fi

docker build -t alpinx $PWD

if [[ -n "$(docker ps -aq -f 'name=alpinx')" ]] ; then
    echo "removing stopped devpi server"
    docker rm --force alpinx
fi

if [[ -z "$(docker ps -aq -f 'name=cache-alpine')" ]] ; then
    echo "creating alpine cache volume"
    docker create -v /var/cache/alpine --name cache-alpine alpine:3.3 /bin/true
fi

echo "starting alpinx server"
docker run -d --restart=always --name alpinx \
    --dns 172.16.0.83 \
    --volumes-from cache-alpine \
    alpinx

