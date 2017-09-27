#!/bin/bash
set -e

echo "################################"
echo "#"
echo "#  In addition to stdout/stderr, check out /usr/share/tomcat7/logs for logs."
echo "#"

cd /usr/share/tomcat7/webapps/ROOT/META-INF
/container-scripts/restart-on-error /usr/share/tomcat7/bin/catalina.sh run
