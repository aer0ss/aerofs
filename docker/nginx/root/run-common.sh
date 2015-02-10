#!/bin/bash
# get the browser cert from config.service, config one if not found, and launch nginx.
set -e

mkdir -p /etc/nginx/certs

echo "Getting browser cert..."
BROWSER_CERT_FILE=/etc/nginx/certs/browser.crt
BROWSER_KEY_FILE=/etc/nginx/certs/browser.key
BROWSER_CERT=$(/container-scripts/get-config-property server.browser.certificate)
BROWSER_KEY=$(/container-scripts/get-config-property server.browser.key)

if [ -z "${BROWSER_CERT}" ] || [ -z "${BROWSER_KEY}" ]; then
    echo "Creating self signed browser cert..."
    # Use a random CNAME because chrome and firefox complain if they encounter two
    # different certificates with the same CNAME/serial pair even if they are signed by a
    # different CA (which is a bug on their part). The randomness appeases the picky Gods
    # of Internet browsing. We don't really care about the CNAME anyway, since this is just
    # the placeholder browser certificate.
    /container-scripts/certify AeroFS-self-signed-browser-cert-${RANDOM} /etc/nginx/certs/browser
    /container-scripts/set-config-property browser_cert "$(cat ${BROWSER_CERT_FILE})"
    /container-scripts/set-config-property browser_key "$(cat ${BROWSER_KEY_FILE})"
else
    echo "$BROWSER_CERT" > ${BROWSER_CERT_FILE}
    echo "$BROWSER_KEY" > ${BROWSER_KEY_FILE}
fi

echo "Starting nginx..."
nginx -g "daemon off;"
