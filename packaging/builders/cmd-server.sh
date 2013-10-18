#!/bin/bash
set -ue

RESOURCES=../src/command/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/cmd-server.yml $RESOURCES/logback.xml"
JAVA_ARGS="-XX:+AggressiveOpts -XX:+UseFastAccessorMethods -Dlogback.configurationFile=./logback.xml"
SERVICE_ARGS="cmd-server.yml 2>> /var/log/cmd-server/cmd-server.err.log"

"$SCRIPT_DIR"/generate_service_deb_template.sh cmd-server "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

DEBIAN=build/cmd-server/DEBIAN

echo "service cmd-server start" >> $DEBIAN/postinst
