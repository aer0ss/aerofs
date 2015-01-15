#!/bin/bash
set -e

mkdir -p /etc/nginx/certs

/container-scripts/copy-ca-cert /etc/nginx/certs

echo "Creating AeroFS internal cert..."
/container-scripts/certify $(/container-scripts/get-config-property base.host.unified) /etc/nginx/certs/aerofs

/get-browser-cert-and-run-nginx.sh
