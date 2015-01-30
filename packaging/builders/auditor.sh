#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

OUTPUT_DIR=build/auditor
RESOURCES=../src/auditor/src/dist

CONFIG="$RESOURCES/auditor.yml"
JAVA_ARGS="-Xmx500m"
SERVICE_ARGS="auditor.yml"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh auditor "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

OPT=$OUTPUT_DIR/opt/auditor
INIT=$OUTPUT_DIR/etc/init

# tweak upstart config
cat << EOF >> $INIT/auditor.conf
# Set a high ulimit for no files to allow a huge # of users to connect
limit nofile 1024000 1024000
EOF

