#!/bin/bash

if [ $# -ne 2 ] && [ $# -ne 3 ]
then
    echo "Usage: $0 <host> <port> [<description>]"
    exit 1
fi

host=$1
port=$2
desc=$3

nc -z -w 1 $host $port

if [ $? -ne 0 ]
then
    if [ "$host" = "localhost" ]
    then
        text="Not listening on port $port"
    else
        text="Not listening on $host:$port"

    fi

    if [ -z "$desc" ]
    then
        text="$text."
    else
        text="$text ($desc)."
    fi

    echo $text
    exit 1
fi
