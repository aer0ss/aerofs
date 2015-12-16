#!/bin/bash
set -e

mkdir -p /etc/nginx/certs

/container-scripts/copy-ca-cert /etc/nginx/certs

echo "Creating AeroFS internal cert..."
/container-scripts/certify $(/container-scripts/get-config-property base.host.unified) /etc/nginx/certs/aerofs
# Append issuer cert onto the end of the cert chain
cat /etc/nginx/certs/cacert.pem >> /etc/nginx/certs/aerofs.crt

/run-common.sh
