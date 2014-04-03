#!/bin/bash
set -ue

RESOURCES=../src/dryad/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/logback.xml $RESOURCES/dryad.properties"
JAVA_ARGS="-Xmx1536m"
SERVICE_ARGS="dryad.properties"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh dryad "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"
