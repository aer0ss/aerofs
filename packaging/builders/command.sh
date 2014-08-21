#!/bin/bash
set -ue

RESOURCES=../src/command/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/command.yml $RESOURCES/logback.xml"
JAVA_ARGS="-XX:+AggressiveOpts -XX:+UseFastAccessorMethods -Dlogback.configurationFile=./logback.xml"
SERVICE_ARGS="command.yml 2>> /var/log/command/command.err.log"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh command "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

DEBIAN=build/command/DEBIAN

echo "service command start" >> $DEBIAN/postinst
