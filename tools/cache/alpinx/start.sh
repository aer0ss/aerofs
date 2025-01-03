#!/bin/bash
set -eu
# alpinx is a poor man's package cacher for Alpine Linux
# it's basically an nginx instance configured as a caching reverse-proxy

PWD="$(cd $(dirname $0); pwd -P)"

success() { echo >&2 -e "\033[32mok: \033[0m- start alpinx"; }

if [[ -n "$(docker ps -q -f 'name=alpinx')" ]] ; then
    echo "alpinx server already running"
    success
    exit 0
fi

docker build -t alpinx $PWD

if [[ -n "$(docker ps -aq -f 'name=alpinx')" ]] ; then
    echo "removing stopped alpinx server"
    docker rm --force alpinx
fi

if [[ -z "$(docker ps -aq -f 'name=cache-alpine')" ]] ; then
    echo "creating alpine cache volume"
    docker create -v /var/cache/alpine --name cache-alpine alpine:3.5 /bin/true
fi

echo "starting alpinx server"
docker run -d --restart=always --name alpinx \
    --dns 8.8.8.8 \
    --volumes-from cache-alpine \
    alpinx
success
