#!/bin/bash
set -eu

cd $(dirname $0)

curl --include --request POST \
    --insecure \
    --header "Content-Type:application/octet-stream" \
    --data-binary "test" \
    https://dryad.aerofs.com/v1.0/appliance/0/not_uuid/logs
