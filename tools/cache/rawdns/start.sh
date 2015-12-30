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
if [[ $(uname -s) == "Darwin" ]] ; then
    # sigh...
    #  1. docker CLI is not flexible enough so we need to use API directly
    #  2. OSX is a broken piece of shit, SecureTransport can't load PEM cert/key so we need to
    #     convert them to PKCS12
    if [[ ! -f $DOCKER_CERT_PATH/client.p12 ]] ; then
        openssl pkcs12 -export -in $DOCKER_CERT_PATH/cert.pem -inkey $DOCKER_CERT_PATH/key.pem \
            -out $DOCKER_CERT_PATH/client.p12 -passout pass:.
    fi

    curl_opts="-k -E $DOCKER_CERT_PATH/client.p12:. https${DOCKER_HOST#tcp}"
    modified=$(stat -f %m $config)
elif [[ $(uname -s) == "Linux" ]] ; then
    curl_opts="--unix-socket /var/run/docker.sock http:"
    modified=$(stat -c %Y $config)
else
    echoerr "Unsupported platform"
    exit 1
fi

# NB: remove sub-second precision and convert from ISO 8601 to Unix epoch
created=$(curl --fail $curl_opts/images/rawdns/json 2>/dev/null | jq -r '.Created[0:19]+"Z" | fromdate')

if [[ -n "$(docker ps -q -f 'name=rawdns')" ]] && (( $created > $modified )); then
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
        docker-machine ssh $VM "echo 'EXTRA_ARGS=\"--userland-proxy=false --dns $BRIDGE \$EXTRA_ARGS\"' | sudo tee -a $profile"

        echo "restarting docker daemon"
        docker-machine ssh $VM "sudo /etc/init.d/docker restart"
        # wait a few seconds because the init script exits early
        sleep 5
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
