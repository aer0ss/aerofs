#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

OUTPUT_DIR=build/baseline-throughput-server
RESOURCES=../src/baseline-throughput-server/src/dist

CONFIG="$RESOURCES/server.yml"
JAVA_ARGS="-Xmx1536m"
SERVICE_ARGS="server.yml"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh baseline-throughput-server "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

OPT=$OUTPUT_DIR/opt/baseline-throughput-server
INIT=$OUTPUT_DIR/etc/init

# tweak postinst to load baseline-throughput-server sysctl config
cat << EOF >> $OUTPUT_DIR/DEBIAN/postinst
invoke-rc.d procps start
EOF

# tweak upstart config
cat << EOF >> $INIT/baseline-throughput-server.conf
# Set a high ulimit for no files to allow a huge # of users to connect
limit nofile 1024000 1024000
EOF
