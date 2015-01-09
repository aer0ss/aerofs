#!/bin/bash
set -e

mkdir -p /etc/nginx/certs

/container-scripts/copy-ca-cert /etc/nginx/certs

########
# Create cert for AeroFS's internal communication

/container-scripts/certify $(/container-scripts/get-config-property base.host.unified) /etc/nginx/certs/aerofs

########
# Create browser cert. Copy it from Config if the user already provided one.

BROWSER_CERT=$(/container-scripts/get-config-property server.browser.certificate)
BROWSER_KEY=$(/container-scripts/get-config-property server.browser.key)

if [ "x$BROWSER_CERT" == x ] || [ "x$BROWSER_KEY" == x ]; then
    echo "Creating self signed browser cert..."
    # Use a random CNAME because chrome and firefox complain if they encounter two
    # different certificates with the same CNAME/serial pair even if they are signed by a
    # different CA (which is a bug on their part). The randomness appeases the picky Gods
    # of Internet browsing. We don't really care about the CNAME anyway, since this is just
    # the placeholder browser certificate.
    /container-scripts/certify AeroFS-browser-cert-$RANDOM /etc/nginx/certs/browser
else
    echo "Copying user provided browser cert..."
    echo "$BROWSER_CERT" > /etc/nginx/certs/browser.crt
    echo "$BROWSER_KEY" > /etc/nginx/certs/browser.key
fi

echo "Starting nginx..."
nginx -g "daemon off;"
