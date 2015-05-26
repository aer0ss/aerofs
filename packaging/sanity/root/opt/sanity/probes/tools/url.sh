#!/bin/bash

if [ $# -ne 1 ]
then
    echo "Usage: $0 <url>"
    exit 1
fi

url="$1"
code=$(curl -k -o /dev/null --silent --head --write-out '%{http_code}\n' "$url")

if [ "$code" != "200" ] && [ "$code" != "204" ]
then
    echo "Error code $code from $url"
    exit 1
fi
