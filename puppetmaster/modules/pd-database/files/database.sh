#!/bin/bash -e

PORTS="3306 6379"

for port in $PORTS
do
    echo "Check localhost:$port"
    nc -z localhost $port
done

echo "SUCCESS!"
