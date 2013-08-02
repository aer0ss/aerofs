#!/bin/bash -e

# We're looking for a line in /etc/restund.conf that looks like:
# tcp_listen           192.168.51.141:3478

addr=$(grep ^tcp_listen /etc/restund.conf | sed 's/\s\s*/ /g' | cut -d ' ' -f 2)
ip=$(echo $addr | cut -d ':' -f 1)
port=$(echo $addr | cut -d ':' -f 2)

/opt/sanity/probes/tools/port.sh $ip $port
