#!/bin/bash

if [ $# != 1 ]; then
    echo "Usage: $0 <host:port>"
    exit 11
fi

URL="http://$1"

echo "Waiting for ${URL} readiness..."
START=$(date +"%s")
while true; do
    BODY="$(curl -s --connect-timeout 1 ${URL} || true)"
    [[ "${BODY}" ]] && break
    if [ $(($(date +"%s")-START)) -gt 300 ]; then
        echo "ERROR: Timeout when waiting for ${URL} readiness"
        exit 22
    fi
    sleep 1
done
