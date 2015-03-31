#!/bin/bash
set -e

mkdir -p /etc/nginx/certs

/container-scripts/copy-ca-cert /etc/nginx/certs

echo "Creating AeroFS internal cert..."
/container-scripts/certify $(/container-scripts/get-config-property base.host.unified) /etc/nginx/certs/aerofs
# Append issuer cert onto the end of the cert chain
cat /etc/nginx/certs/cacert.pem >> /etc/nginx/certs/aerofs.crt

echo "Writing status monitoring service's htpasswd file..."
MONITOR_USER=$(/container-scripts/get-config-property monitoring.username)
MONITOR_PASS=$(/container-scripts/get-config-property monitoring.password)
# Path must be consistent with the value in /etc/nginx/sites/status
FILE=/status.htpasswd

if [ -z "${MONITOR_USER}" ] || [ -z "${MONITOR_PASS}" ]; then
    echo "Deleting ${FILE}..."
    rm -rf "${FILE}"
else
    SHAED_PASS="$(echo -n "${MONITOR_PASS}" | openssl sha1 -binary | base64)"
    echo "${MONITOR_USER}:{SHA}${SHAED_PASS}" > "${FILE}"
fi

/run-common.sh
