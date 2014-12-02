#!/bin/bash
set -ue

RESOURCES=../src/command/src/dist
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/command.yml"
JAVA_ARGS="-cp . -XX:MaxDirectMemorySize=256m -XX:+AggressiveOpts -XX:+UseFastAccessorMethods"
SERVICE_ARGS="command.yml"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh command "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

DEBIAN=build/command/DEBIAN

echo "service command start" >> $DEBIAN/postinst
