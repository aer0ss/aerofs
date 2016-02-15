#!/bin/bash
# rawdns provides a Docker-aware DNS server.  You'll need to configure your
# Docker daemon to use it by default (i.e. "--dns 172.17.42.1"), and configure
# your host system's resolver as well.
#
# github.com/tianon/rawdns

set -e

# To print on stderr
echoerr() { echo "$@" 1>&2; }

PWD="$( cd $(dirname $0) ; pwd -P )"

# detect docker bridge IP
BRIDGE=$(docker run --rm alpine:3.3 ip route | grep default | cut -d ' ' -f 3)

# check if rawdns config was modified since the image was created
config=$PWD/root/rawdns.json

if [[ -n "$(docker ps -q -f 'name=rawdns')" ]] && "$PWD/../img_fresh.sh" rawdns "$config"; then
    echo "rawdns already running"
else
    echo "build rawdns configured image"
    curl -o $PWD/root/rawdns -sSL "https://github.com/tianon/rawdns/releases/download/1.3/rawdns-amd64"
    chmod +x $PWD/root/rawdns

    docker build -t rawdns $PWD

    if [[ -n "$(docker ps -aq -f 'name=rawdns')" ]] ; then
        echo "removing stopped rawdns"
        docker rm --force rawdns
    fi

    echo "starting rawdns on $BRIDGE"
    docker run -d --restart=always --name rawdns \
            -p $BRIDGE:53:53/udp -p $BRIDGE:53:53/tcp \
            -v /var/run/docker.sock:/var/run/docker.sock \
            rawdns
fi

if docker run --rm alpine:3.3 ping -c 1 rawdns.docker &>/dev/null ; then
    echo "dns already configured"
    exit 0
fi

VM=${1:-$(docker-machine active 2>/dev/null || echo "docker-dev")}

if docker-machine ls "$VM" &>/dev/null ; then
    echo "updating docker dameon dns config"
    
    profile=/var/lib/boot2docker/profile
    service=/etc/systemd/system/docker.service

    # detect old boot2docker vs new b2d-ng from docker-machine 0.4+
    os=$(docker-machine ssh $VM "if [ -f $service ] ; then \
        echo b2d-ng ; elif [ -f $profile ] ; then \
        echo b2d ; else \
        echo unsupported ; fi")

    echo "detected $VM as $os"

    if [[ "$os" == "b2d" ]] ; then
        # configure docker daemon to use rawdns resolver
        # boot2docker init script doesn't do a proper restart... need to do it oursevles...
        docker-machine ssh $VM <<EOF
        echo 'EXTRA_ARGS="--userland-proxy=false --dns $BRIDGE \$EXTRA_ARGS"' | sudo tee -a $profile
        echo "restarting docker daemon"
        sudo /etc/init.d/docker stop
        while sudo /etc/init.d/docker status | grep -F "is running" &>/dev/null ; do
            sleep 1
        done
        echo "stopped"
        sudo /etc/init.d/docker start
        while sudo /etc/init.d/docker status | grep -F "is not running" &>/dev/null ; do
            sleep 1
        done
        echo "started"
EOF
    elif [[ "$os" == "b2d-ng" ]] ; then
        # update EXTRA_ARGS to use rawdns resolver [idempotent]
        extra="Environment=\"EXTRA_ARGS=--userland-proxy=false --dns $BRIDGE\""

        docker-machine ssh $VM "if [ \$(grep -s -F '$extra' ${service}.d/dns.conf | wc -l) -eq 0 ] ; then \
            sudo mkdir -p ${service}.d ; \
            echo '[Service]' | sudo tee ${service}.d/dns.conf ; \
            echo '$extra' | sudo tee -a ${service}.d/dns.conf ; fi"

        # ensure ExecStart picks up EXTRA_ARGS [idempotent]
        docker-machine ssh $VM "if [ \$(grep -E \"^ExecStart=\" $service | grep -v -F \"\\\$EXTRA_ARGS\" | wc -l) -eq 1 ] ; then \
            cat $service | sed 's/^ExecStart=\\(.*\\)/ExecStart=\\1 \\\$EXTRA_ARGS/' | sudo tee ${service}.new ; \
            sudo mv ${service}.new ${service} ; fi"
        
        echo "restarting docker daemon"
        docker-machine ssh $VM "sudo systemctl daemon-reload && sudo systemctl restart docker"
    else
        echoerr "unsupported OS"
        exit 1
    fi
else
    # TODO: automatically adjust dns setting for raw docker env
    echoerr 'Manually add "--dns $BRIDGE" to the options of your docker daemon and restart it'
    exit 1
fi

# sanity check
if docker run --rm alpine:3.3 ping -c 1 rawdns.docker &>/dev/null ; then
    echo "dns successfully configured"
else
    echoerr "invalid dns configuration"
    exit 1
fi
