#!/bin/bash
set -eu
# rawdns provides a Docker-aware DNS server.  You'll need to configure your
# Docker daemon to use it by default (i.e. "--dns 172.17.42.1"), and configure
# your host system's resolver as well.
#
# github.com/tianon/rawdns

PWD="$(cd $(dirname $0); pwd -P)"

failure() { echo >&2 -e "\033[31merr: \033[0m- start rawdns ($1)"; }
success() { echo >&2 -e "\033[32mok: \033[0m- start rawdns"; }

# detect docker bridge IP
BRIDGE=$(docker run --rm alpine:3.5 ip route | grep default | cut -d ' ' -f 3)

# check if rawdns config was modified since the image was created
config=$PWD/root/rawdns.json

if [[ -n "$(docker ps -q -f 'name=rawdns')" ]] && "$PWD/../img_fresh.sh" rawdns "$config"; then
    echo "rawdns already running"
else
    echo "build rawdns configured image"
    curl -o $PWD/root/rawdns -sSL "https://github.com/tianon/rawdns/releases/download/1.10/rawdns-amd64"
    chmod +x $PWD/root/rawdns

    # ensure we get a fresh timestamp or img_fresh will cause repeated rebuild
    docker build --no-cache=true -t rawdns $PWD

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

if docker run --rm alpine:3.5 ping -c 1 rawdns.docker &>/dev/null ; then
    echo "dns already configured"
    success
    exit 0
fi

VM=${1:-"docker-dev"}

if colima status "$VM" &>/dev/null ; then
    echo "updating colima docker daemon dns config: $BRIDGE"

    # configure docker daemon to use rawdns resolver
    conf=$(
      colima -p "$VM" ssh cat /etc/docker/daemon.json \
      | tee -a /dev/stderr \
      | jq '. + {"dns": ["'$BRIDGE'"]}'
    )
    # NB: need to break the pipe to avoid corrupting the file
    echo "$conf" | colima -p "$VM" ssh sudo tee /etc/docker/daemon.json

    echo "restarting docker daemon"
    colima -p "$VM" ssh sudo systemctl restart docker
else
    # TODO: automatically adjust dns setting for raw docker env
    failure "manually add '--dns $BRIDGE' to the options of your docker daemon and restart it"
    exit 1
fi

# sanity check
if docker run --rm alpine:3.5 ping -c 1 rawdns.docker &>/dev/null ; then
    echo "dns successfully configured"
    success
else
    failure "invalid dns configuration"
    exit 1
fi
