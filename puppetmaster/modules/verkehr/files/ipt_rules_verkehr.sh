#!/bin/sh
IPT="/sbin/iptables"

$IPT -F
$IPT -t nat -A PREROUTING -i eth0 -p tcp --dport 443 -j REDIRECT --to-port 29438
