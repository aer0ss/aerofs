#!/bin/bash
set -e
#
# This script blocks until the agent program quits
#

[[ $# == 2 ]] || {
    echo "Usage: $0 <agent-name> <ci-url>"
    exit 11
}
AGENT_NAME="$1"
TC_SERVER_HOST="$2"

TC_AGENT_HOME=/teamcity-agent

if [ ! -f ${TC_AGENT_HOME}/bin/agent.sh ]; then
    echo "Downloading TeamCity Agent ..."
    wget --no-check-certificate ${TC_SERVER_HOST}/update/buildAgent.zip -O /download.zip
    cd ${TC_AGENT_HOME}
    unzip /download.zip
    chmod +x bin/*.sh
    rm /download.zip

    sed -e "s!^serverUrl=.*!serverUrl=${TC_SERVER_HOST}/!" \
        -e "s!^name=.*!name=${AGENT_NAME}!" ${TC_AGENT_HOME}/conf/buildAgent.dist.properties \
        > ${TC_AGENT_HOME}/conf/buildAgent.properties
fi

echo "Starting TeamCity agent ..."
echo
echo ">>> See /teamcity-agent/logs/teamcity-agent.log for more logs"
echo
/teamcity-agent/bin/agent.sh run
