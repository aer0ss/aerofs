#!/bin/bash
set -eu

cd $(dirname $0)

# requires PUT requests, not POST
curl --include --request POST \
    --insecure \
    --header "Content-Type:application/octet-stream" \
    --data-binary "test" \
    https://dryad.aerofs.com/v1.0/defects/00000000000000000000000000000000/appliance
