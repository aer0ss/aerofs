#!/bin/bash
set -eu

cd $(dirname $0)

curl --include --request POST \
    --insecure \
    --header "Content-Type:application/octet-stream" \
    --data-binary "test" \
    https://dryad.aerofs.com/v1.0/appliance/0/00000000000000000000000000000000/logs

curl -i -X POST \
    --insecure \
    --header "Content-Type:application/octet-stream" \
    --data-binary "test" \
    https://dryad.aerofs.com/v1.0/client/0/00000000000000000000000000000000/matt@aerofs.com/00000000000000000000000000000000/logs
