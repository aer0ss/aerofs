#!/bin/bash
set -e

ARG_NUTS=no-unittest-services

[[ $# = 2 ]] || [[ $3 = ${ARG_NUTS} ]] || {
    echo "Usage: $0 <agent-name> <ci-url> [${ARG_NUTS}]"
    exit 11
}
AGENT_NAME="$1"
TEAMSERVER_HOST="$2"

if [ "$2" = ${ARG_NUTS} ]; then
    START_UNITTEST_SERVICES="echo Unittest services disabled"
else
    START_UNITTEST_SERVICES=/scripts/start-unittest-services.sh
fi

echo "Building agent container image ..."
docker build -t teamcity-agent "$(dirname "$0")"

echo "Launching agent container ..."
docker run -d -p 9090:9090 --name ${AGENT_NAME} \
    -v /var/run/docker.sock:/var/run/docker.sock \
    teamcity-agent bash -c "${START_UNITTEST_SERVICES} && /scripts/run-teamcity-agent.sh ${AGENT_NAME} ${TEAMSERVER_HOST}"

echo "Agent container has launched. 'docker logs -f ${AGENT_NAME}' to see progress."
