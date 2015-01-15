#!/bin/bash
set -e

echo "################################"
echo "#"
echo "#  In addition to stdout/stderr, check out /usr/share/tomcat6/logs for logs."
echo "#"

/usr/share/tomcat6/bin/catalina.sh run
