#!/bin/sh
set -e

/usr/sbin/postfix start
# NB: ideally we'd tail postfix logs
# unfortunately it uses syslog, which requires a functional init system
tail -f /dev/null
