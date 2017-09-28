#!/bin/bash
set -eu
# apt-cacher-ng provides package caching for debian/ubuntu

PWD="$(cd $(dirname $0); pwd -P)"

success() { echo >&2 -e "\033[32mok: \033[0m- start apt-cacher-ng"; }

if [[ -n "$(docker ps -q -f 'name=apt-cacher-ng')" ]] ; then
    echo "apt-cacher-ng already running"
    success
    exit 0
fi

docker build -t apt-cacher-ng $PWD

if [[ -n "$(docker ps -aq -f 'name=apt-cacher-ng')" ]] ; then
    echo "removing stopped apt-cacher-ng"
    docker rm --force apt-cacher-ng
fi

if [[ -z "$(docker ps -aq -f 'name=cache-apt')" ]] ; then
    echo "creating apt cache volume"
    docker create -v /var/cache/apt-cacher-ng --name cache-apt alpine:3.5 /bin/true
fi

echo "starting apt-cacher-ng"
# We specify --dns for this container so that it doesn't clash with rawdns
docker run -d --restart=always --name apt-cacher-ng \
        --dns 8.8.8.8 \
        --volumes-from cache-apt \
        apt-cacher-ng
success
