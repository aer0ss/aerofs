#!/bin/bash
set -e

if [ $# -ne 1 ]
then
    echo "Usage: $0 <metrics_directory>"
    exit 1
fi


DIR=$1
mkdir -p "$DIR"

while :
do
    FILE=$(date -u "+%H-%M-%Sd%m-%d.json")
    echo "recording metrics into "$DIR"/$FILE"
    docker exec polaris curl -s -X POST localhost:8087/commands/metrics > "$DIR"/$FILE
    sleep 5
done
