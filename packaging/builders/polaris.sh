#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

OUTPUT_DIR=build/polaris
RESOURCES=../src/polaris/src/dist

CONFIG="$RESOURCES/polaris.yml.tmplt"
JAVA_ARGS="-Xmx1536m"
SERVICE_ARGS="polaris.yml"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh polaris "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

OPT=$OUTPUT_DIR/opt/polaris
INIT=$OUTPUT_DIR/etc/init

# tweak postinst to load polaris sysctl config
cat << EOF >> $OUTPUT_DIR/DEBIAN/postinst
invoke-rc.d procps start
EOF

# tweak upstart config
cat << EOF >> $INIT/polaris.conf
# Set a high ulimit for no files to allow a huge # of users to connect
limit nofile 1024000 1024000
EOF
