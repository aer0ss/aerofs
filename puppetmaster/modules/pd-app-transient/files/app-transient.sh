#!/bin/bash -e

PORTS="443 3478 5222 8888 29438"

for port in $PORTS
do
    echo "Check localhost:$port"
    nc -z localhost $port
done

echo "SUCCESS!"
