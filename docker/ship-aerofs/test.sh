#!/bin/bash
set -ex
#
# This script drives Ship Enterprise's testing. It's supposed to run in AeroFS's CI agent containers which have been
# authenticated with Vault.
#

# Request for a temporary AWS key for EC2 access
KEYS="$(vault read -format=json aws/creds/ec2)"
ACCESS_KEY="$(jq -r .data.access_key <<< "${KEYS}")"
SECRET_KEY="$(jq -r .data.secret_key <<< "${KEYS}")"
LEASE_ID="$(jq -r .lease_id <<< "${KEYS}")"

set +e
"$(dirname "$0")/../ship/test/run.sh" aerofs "${ACCESS_KEY}" "${SECRET_KEY}"
EXIT=$?
set -e

# Clean up
vault token-revoke "${LEASE_ID}"

exit ${EXIT}