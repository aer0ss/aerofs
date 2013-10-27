#!/bin/bash
set -ue

RESOURCES=../src/bifrost/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/logback.xml $RESOURCES/bifrost.properties"
JAVA_ARGS=""
SERVICE_ARGS="bifrost.properties"

"$SCRIPT_DIR"/generate_service_deb_template.sh bifrost "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

OUTPUT_DIR=build/bifrost
mkdir -p $OUTPUT_DIR/opt/bifrost
cp $RESOURCES/schemas/bifrost.sql $OUTPUT_DIR/opt/bifrost/bifrost.sql

# TODO: scrypt
