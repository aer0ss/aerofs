#!/bin/bash
set -ue

RESOURCES=../src/dryad/resources
SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONFIG="$RESOURCES/logback.xml $RESOURCES/dryad.properties $RESOURCES/banner.txt $RESOURCES/ip_blacklist.conf $RESOURCES/user_blacklist.conf $RESOURCES/device_blacklist.conf"
JAVA_ARGS="-Xmx1536m"
SERVICE_ARGS="dryad.properties"

"$SCRIPT_DIR"/generators/generate_service_deb_template.sh dryad "$CONFIG" "$JAVA_ARGS" "$SERVICE_ARGS"

# replace the following files because of manual edits
for file in control postinst
do
    cp dryad/DEBIAN/"$file" build/dryad/DEBIAN
done
