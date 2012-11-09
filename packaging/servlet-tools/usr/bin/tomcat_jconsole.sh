#!/bin/bash

jmxlocal=$(cat /etc/hosts | head -1 | grep jmxlocal)

if [ -z "$jmxlocal" ]
then
    echo "Setting up loopback reference for jmxlocal..."
    echo "127.0.0.1 jmxlocal" >> /etc/hosts.tmp
    cat /etc/hosts >> /etc/hosts.tmp
    mv /etc/hosts.tmp /etc/hosts
fi

echo "Stopping tomcat6 service..."
service tomcat6 stop

on_die()
{
    echo "Restarting tomcat..."
    service tomcat6 stop
    service tomcat6 start
    exit 0
}

trap on_die TERM SIGINT SIGTERM
echo "Starting jconsole enabled service..."

/usr/lib/jvm/default-java/bin/java \
    -Djava.util.logging.config.file=/var/lib/tomcat6/conf/logging.properties \
    -Djava.awt.headless=true \
    -Xmx128m \
    -XX:+UseConcMarkSweepGC \
    -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager \
    -Djava.endorsed.dirs=/usr/share/tomcat6/endorsed \
    -classpath /usr/share/tomcat6/bin/bootstrap.jar \
    -Dcatalina.base=/var/lib/tomcat6 \
    -Dcatalina.home=/usr/share/tomcat6 \
    -Djava.io.tmpdir=/tmp/tomcat6-tomcat6-tmp \
    -Dcom.sun.management.jmxremote \
    -Dcom.sun.management.jmxremote.port=9999 \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Djava.rmi.server.hostname=jmxlocal \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/aerofs org.apache.catalina.startup.Bootstrap start
