#!/bin/bash
set -eu

cd $(dirname $0)

curl --include --request PUT \
    --insecure \
    --header "Content-Type:application/octet-stream" \
    --data-binary "test" \
    https://dryad.aerofs.com/v1.0/defects/00000000000000000000000000000000/client/test@example.com/00000000000000000000000000000000

curl --include --request PUT \
    --insecure \
    --header "Content-Type:application/octet-stream" \
    --data-binary "test" \
    https://dryad.aerofs.com/v1.0/defects/00000000000000000000000000000000/appliance

curl --include --request PUT \
    --insecure \
    --header "Content-Type:application/octet-stream" \
    --data-binary "test" \
    https://dryad.aerofs.com/v1.0/archived/test@example.com/00000000000000000000000000000000/logs.zip
