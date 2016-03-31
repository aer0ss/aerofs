#!/bin/bash -e

if [  -f /host/keys/domain.crt ]; then
    cp /host/keys/domain.crt /host/keys/https.crt
    cp /host/keys/domain.key /host/keys/https.key
fi
nginx -g "daemon off;"
