#!/bin/bash
set -ue

RESOURCES=../src/verkehr/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/verkehr.yml"
#-XX:+UseBiasedLocking -XX:+UseStringCache -XX:+OptimizeStringConcat
#XXX (AG): add this to enable SSL debugging
#   -Djavax.net.debug=all
JAVA_ARGS="-Xmx1536m"
SERVICE_ARGS="server verkehr.yml"

"$SCRIPT_DIR"/generate_service_deb_template.sh verkehr "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

OPT=verkehr/opt/verkehr
INIT=verkehr/etc/init

# tweak upstart config
cat << EOF >> $INIT/verkehr.conf
# Set a high ulimit for no files to allow a huge # of users to connect
limit nofile 1024000 1024000
EOF

# verkehr command-line clients (uses same java code as AeroFS client)
cp $RESOURCES/subscriber $OPT
cp $RESOURCES/publisher $OPT
