#!/bin/bash
set -ue

RESOURCES=../src/havre/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/logback.xml $RESOURCES/havre.properties"
JAVA_ARGS="-Xmx1536m"
SERVICE_ARGS="havre.properties"

"$SCRIPT_DIR"/generate_service_deb_template.sh havre "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"
