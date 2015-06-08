#!/bin/bash

PWD="$( cd $(dirname $0) ; pwd -P )"
set -x

# OS X / docker-machine
# TODO: auto-detect host and support linux
APT_CACHE_DIR=/var/cache/apt-cacher-ng

# create cache dir
docker-machine ssh docker-dev "mkdir -p $APT_CACHE_DIR"

# collection of cache/proxy containers!

# rawdns provides a Docker-aware DNS server.  You'll need to configure your
# Docker daemon to use it by default (i.e. "--dns 172.17.42.1"), and configure
# your host system's resolver as well.
#
# github.com/tianon/rawdns
docker run -d --restart=always --name rawdns \
        -p 53:53/udp -p 53:53/tcp \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v $PWD/rawdns.json:/etc/rawdns.json:ro \
        tianon/rawdns rawdns /etc/rawdns.json


# configure docker daemon to use rawdns resolver
# FIXME: boot2docker is very minimalist so we have to use sed, which is pretty fragile...
docker-machine ssh docker-dev "sudo cat /var/lib/boot2docker/profile | sed \"s/EXTRA_ARGS='.*--tlsverify/EXTRA_ARGS='--dns 172.17.42.1 --tlsverify/\" | sudo tee cat /var/lib/boot2docker/profile"

# restart docker daemon (the the init script is borked and cannot restart properly...)
docker-machine ssh docker-dev "sudo /etc/init.d/docker stop ; sleep 5 ; sudo /etc/init.d/docker start"

# apt-cacher-ng provides package caching for debian/ubuntu.  We specify --dns
# for this container so that it doesn't clash with rawdns.
#
# github.com/tianon/dockerfiles
docker run -d --restart=always --name apt-cacher-ng \
        --dns 8.8.8.8 --dns 8.8.4.4 -v $APT_CACHE_DIR:/var/cache/apt-cacher-ng \
        tianon/apt-cacher-ng

