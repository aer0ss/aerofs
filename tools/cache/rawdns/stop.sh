#!/bin/bash
set -eu

failure() { echo >&2 -e "\033[31merr: \033[0m- stop rawdns ($1)"; }

docker rm -f rawdns || true

VM=${1:-"docker-dev"}

if colima status "$VM" &>/dev/null ; then
    echo "removing dns config from docker daemon"

    # configure docker daemon to use rawdns resolver
    conf=$(
      colima -p "$VM" ssh cat /etc/docker/daemon.json \
      | tee -a /dev/stderr \
      | jq 'del(.dns)'
    )
    # NB: need to break the pipe to avoid corrupting the file
    echo "$conf" | colima -p "$VM" ssh sudo tee /etc/docker/daemon.json

    echo "restarting docker daemon"
    colima -p "$VM" ssh sudo systemctl restart docker
else
    failure "dns config must be manually fixed on raw docker"
fi
