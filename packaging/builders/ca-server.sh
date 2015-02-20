#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

OUTPUT_DIR=build/ca-server
RESOURCES=../src/ca-server/src/dist

CONFIG="$RESOURCES/ca.yml.tmplt ../src/bunker/migration.sh"
JAVA_ARGS="-Xmx1536m"
SERVICE_ARGS="ca.yml"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh ca-server "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"
