#!/bin/bash
# apt-cacher-ng provides package caching for debian/ubuntu

set -e

PWD="$( cd $(dirname $0) ; pwd -P )"

if [[ -n "$(docker ps -q -f 'name=apt-cacher-ng')" ]] ; then
    echo "apt-cacher-ng already running"
    exit 0
fi

docker build -t apt-cacher-ng $PWD

if [[ -n "$(docker ps -aq -f 'name=apt-cacher-ng')" ]] ; then
    echo "removing stopped apt-cacher-ng"
    docker rm --force apt-cacher-ng
fi

if [[ -z "$(docker ps -aq -f 'name=cache-apt')" ]] ; then
    echo "creating apt cache volume"
    docker create -v /var/cache/apt-cacher-ng --name cache-apt alpine:3.3 /bin/true
fi

echo "starting apt-cacher-ng"
# We specify --dns for this container so that it doesn't clash with rawdns
docker run -d --restart=always --name apt-cacher-ng \
        --dns 172.16.0.83 \
        --volumes-from cache-apt \
        apt-cacher-ng

