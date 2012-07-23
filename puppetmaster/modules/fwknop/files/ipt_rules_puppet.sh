#!/bin/sh
IPT="/sbin/iptables"

$IPT -F
$IPT -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
$IPT -A INPUT -p tcp --dport 22 -j DROP
