#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

OUTPUT_DIR=build/verkehr
RESOURCES=../src/verkehr/src/dist

CONFIG="$RESOURCES/verkehr.yml"
#-XX:+UseBiasedLocking -XX:+UseStringCache -XX:+OptimizeStringConcat
#XXX (AG): add this to enable SSL debugging
#   -Djavax.net.debug=all
JAVA_ARGS="-Xmx1536m"
SERVICE_ARGS="verkehr.yml"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh verkehr "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

OPT=$OUTPUT_DIR/opt/verkehr
INIT=$OUTPUT_DIR/etc/init

# tweak postinst to load verkehr sysctl config
cat << EOF >> $OUTPUT_DIR/DEBIAN/postinst
invoke-rc.d procps start
EOF

# tweak upstart config
cat << EOF >> $INIT/verkehr.conf
# Set a high ulimit for no files to allow a huge # of users to connect
limit nofile 1024000 1024000
EOF
