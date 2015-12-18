#!/bin/bash
set -e

mkdir -p \
    /data/bunker \
    /data/web \
    /data/redis

if [ ! -f /data/deployment_secret ]
then
    echo "Creating deployment secret..."
    dd if=/dev/urandom bs=16 count=1 | hexdump -v -e '8/2 "%04x"' > /data/deployment_secret
else
    echo "Deployment secret already created. No-op."
fi

echo "Exiting..."
