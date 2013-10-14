#!/bin/bash
set -ue

RESOURCES=../src/devman/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/devman.yml"
JAVA_ARGS="-XX:+AggressiveOpts -XX:+UseFastAccessorMethods"
SERVICE_ARGS="devman.yml"

"$SCRIPT_DIR"/generate_service_deb_template.sh devman "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"
