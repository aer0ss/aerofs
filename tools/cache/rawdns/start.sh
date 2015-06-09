#!/bin/bash
# rawdns provides a Docker-aware DNS server.  You'll need to configure your
# Docker daemon to use it by default (i.e. "--dns 172.17.42.1"), and configure
# your host system's resolver as well.
#
# github.com/tianon/rawdns

PWD="$( cd $(dirname $0) ; pwd -P )"

if [[ -n "$(docker ps -q -f 'name=rawdns')" ]] ; then
    echo "rawdns already running"
else
    echo "build rawdns base image"
    $PWD/../../../golang/builder/build.sh aerofs/rawdns github.com/tianon/rawdns $PWD/Dockerfile.base

    echo "build rawdns configured image"
    docker build -t rawdns $PWD

    if [[ -n "$(docker ps -aq -f 'name=rawdns')" ]] ; then
        echo "removing stopped rawdns"
        docker rm --force rawdns
    fi

    echo "starting rawdns"
    docker run -d --restart=always --name rawdns \
            -p 53:53/udp -p 53:53/tcp \
            -v /var/run/docker.sock:/var/run/docker.sock \
            rawdns
fi

if docker run --rm debian:sid ping -c 1 rawdns.docker &>/dev/null ; then
    echo "dns already configured"
    exit 0
fi

if docker-machine ls docker-dev &>/dev/null ; then
    echo "updating docker dameon dns config"
    # configure docker daemon to use rawdns resolver
    # FIXME: boot2docker is very minimalist so we have to use sed, which is pretty fragile...
    docker-machine ssh docker-dev "sudo cat /var/lib/boot2docker/profile | sed \"s/EXTRA_ARGS='.*--tlsverify/EXTRA_ARGS='--dns 172.17.42.1 --tlsverify/\" | sudo tee cat /var/lib/boot2docker/profile"

    echo "restarting docker daemon"
    # restart docker daemon (the the init script is borked and cannot restart properly...)
    docker-machine ssh docker-dev "sudo /etc/init.d/docker stop ; sleep 5 ; sudo /etc/init.d/docker start"
else
    # TODO: automatically adjust dns setting for raw docker env
    echo 'Manually add "--dns 172.17.42.1" to the options of your docker daemon and restart it'
    exit 1
fi

# sanity check
if docker run --rm debian:sid ping -c 1 rawdns.docker &>/dev/null ; then
    echo "dns successfully configured"
else
    echo "invalid dns configuration"
    exit 1
fi

