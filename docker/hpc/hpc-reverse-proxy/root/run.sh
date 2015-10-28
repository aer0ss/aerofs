#!/bin/sh

# Since /etc/nginx/conf.d/ is a volume there might be old configs there from a previous instance.
# So make sure we remove everything:
rm /etc/nginx/conf.d/*

exec /usr/sbin/nginx -c /etc/nginx/nginx.conf -g "daemon off;"