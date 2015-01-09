#!/bin/bash

if [ $# -ne 1 ]
then
    echo "Usage: $0 <url>"
    exit 1
fi

url="$1"
curl -k -o /dev/null --silent --head --write-out '%{http_code}\n' "$url"

if [ "$?" != "0" ]; then
    echo "'curl $url' exited with code $?"
    exit 1
fi
