#!/bin/bash -e

# We're looking for a line in /etc/restund.conf that looks like:
# tcp_listen           192.168.51.141:3478

typeset line=$(grep ^tcp_listen /etc/restund.conf)
typeset addr=${line##* }
ip=${addr%%:*}
port=${addr##*:}

/opt/sanity/probes/tools/port.sh $ip $port
