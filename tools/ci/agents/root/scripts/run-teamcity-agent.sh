#!/bin/bash
set -e
#
# This script blocks until the agent program quits
#

$(dirname $0)/start-test-services.sh

echo "Starting TeamCity agent ..."
echo
echo ">>> See /teamcity-agent/logs/teamcity-agent.log for more logs"
echo
/teamcity-agent/bin/agent.sh run
