#!/bin/bash
set -e
#
# This script blocks until the agent program quits
#

[[ $# == 1 ]] || {
    echo "Usage: $0 <agent-name>"
    exit 11
}
AGENT_NAME="$1"

$(dirname $0)/start-test-services.sh

TC_AGENT_HOME=/teamcity-agent

if [ ! -d ${TC_AGENT_HOME} ]; then
    echo "Downloading TeamCity Agent ..."
    CI_URL=https://ci.arrowfs.org
    wget --no-check-certificate ${CI_URL}/update/buildAgent.zip -O /download.zip
    mkdir -p ${TC_AGENT_HOME}
    cd ${TC_AGENT_HOME}
    unzip /download.zip
    chmod +x bin/*.sh
    rm /download.zip

    sed -e "s!^serverUrl=.*!serverUrl=${CI_URL}/!" \
        -e "s!^name=.*!name=${AGENT_NAME}!" ${TC_AGENT_HOME}/conf/buildAgent.dist.properties \
        > ${TC_AGENT_HOME}/conf/buildAgent.properties
fi

echo "Starting TeamCity agent ..."
echo
echo ">>> See /teamcity-agent/logs/teamcity-agent.log for more logs"
echo
/teamcity-agent/bin/agent.sh run
