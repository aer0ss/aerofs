#!/bin/bash

proxy()
{
    ssh -f -D8888 $1 "while true; do sleep 1; done"
}

jc()
{
    jconsole \
        -pluginpath $(dirname $0)/topthreads.jar \
        -J-DsocksProxyHost=localhost \
        -J-DsocksProxyPort=8888 \
        service:jmx:rmi:///jndi/rmi://jmxlocal:9999/jmxrmi
}

if [ $# -ne 1 ]
then
    echo "Usage: $0 <tomcat_server>"
    exit 1
fi

echo "Start proxy..."
proxy $1

echo "Running jconsole..."
jc

echo
echo "Stop proxy..."
ssh_pid=`ps ax | grep "[s]sh -f -D8888" | grep $1 | awk '{print $1}'`
kill $ssh_pid
