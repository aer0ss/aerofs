#!/bin/bash
set -e -u

OUTPUT_DIR=build/zephyr
RESOURCES=../src/zephyr/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/logback.xml $RESOURCES/banner.txt"
JAVA_ARGS="-d64 -Xms712m -Xmx1536m"
SERVICE_ARGS="0.0.0.0 8888"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh zephyr "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

INIT=$OUTPUT_DIR/etc/init

# tweak upstart config
cat << EOF >> $INIT/zephyr.conf
# Set a high ulimit for no files to allow a huge # of users to connect
limit nofile 1024000 1024000
EOF
