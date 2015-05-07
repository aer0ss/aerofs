#!/bin/bash
set -e

THIS_DIR="$(dirname ${BASH_SOURCE[0]})"

echo "Building agent container image ..."
docker build -t teamcity-agent "${THIS_DIR}"

echo "Launching agent container ..."
# --net=host for TCP transport tests to pass
# $(hostname):127.0.0.1 otherwise hostname resolution may fail (caused by --net=host)
docker run -d -p 9090:9090 --name teamcity-agent \
    --net=host \
    --add-host $(hostname):127.0.0.1 \
    --add-host ci.arrowfs.org:172.19.10.154 \
    --add-host repos.arrowfs.org:172.16.1.76 \
    teamcity-agent /scripts/run-teamcity-agent.sh

echo 'Agent container has launched. `docker logs -f teamcity-agent` to see progress.'
