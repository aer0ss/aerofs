#!/bin/bash
set -e

[[ $# == 1 ]] || {
    echo "Usage: $0 <agent-name>"
    exit 11
}
AGENT_NAME="$1"

THIS_DIR="$(dirname ${BASH_SOURCE[0]})"

echo "Building agent container image ..."
docker build -t teamcity-agent "${THIS_DIR}"

echo "Launching agent container ..."
# --net=host for TCP transport tests to pass.
# $(hostname):127.0.0.1 otherwise hostname resolution may fail (caused by --net=host)
# --dns can't be used with --net and hence the --add-hosts
docker run -d -p 9090:9090 --name ${AGENT_NAME} \
    --net=host \
    --add-host $(hostname):127.0.0.1 \
    --add-host ci.arrowfs.org:172.19.10.154 \
    --add-host repos.arrowfs.org:172.16.1.76 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    teamcity-agent /scripts/run-teamcity-agent.sh ${AGENT_NAME}

echo "Agent container has launched. 'docker logs -f ${AGENT_NAME}' to see progress."
