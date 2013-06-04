#!/bin/bash

if [ $# -ne 3 ]
then
    echo "Usage: $0 <host> <port> <description>"
    exit 1
fi

host=$1
port=$2
desc=$3

nc -z $host $port

if [ $? -ne 0 ]
then
    echo "Error: $host:$port failed (description: $desc)."
    exit 1
fi
