#!/bin/bash
set -ue

RESOURCES=../src/sparta/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/logback.xml $RESOURCES/sparta.properties"
JAVA_ARGS=""
SERVICE_ARGS="sparta.properties"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh sparta "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"
