#!/bin/bash -e

PORTS="443 1029 8080"

for port in $PORTS
do
    echo "Check localhost:$port"
    nc -z localhost $port
done

echo "SUCCESS!"
