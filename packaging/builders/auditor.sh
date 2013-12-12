#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

OUTPUT_DIR=build/auditor
RESOURCES=../src/auditor/resources

CONFIG="$RESOURCES/logback.xml $RESOURCES/auditor.properties"
#-XX:+UseBiasedLocking -XX:+UseStringCache -XX:+OptimizeStringConcat
#XXX (AG): add this to enable SSL debugging
#   -Djavax.net.debug=all
JAVA_ARGS="-Xmx500m"
SERVICE_ARGS="auditor.properties"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh auditor "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

OPT=$OUTPUT_DIR/opt/auditor
INIT=$OUTPUT_DIR/etc/init

# tweak upstart config
cat << EOF >> $INIT/auditor.conf
# Set a high ulimit for no files to allow a huge # of users to connect
limit nofile 1024000 1024000
EOF

