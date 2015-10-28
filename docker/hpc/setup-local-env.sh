#!/bin/bash
set -e

# This script will setup hpc-reverse-proxy so that you can run it on your own docker-machine with
# the syncfs.com domain. See the README for more information on how to run locally.

TEST_DOMAIN="syncfs.com"

THIS_DIR=$(dirname "${BASH_SOURCE[0]}")

# patch the nginx config template
sed -i "" -e "s/$domain := \"aerofs.com\"/$domain := \"${TEST_DOMAIN}\"/" \
    "${THIS_DIR}/hpc-docker-gen/root/nginx.tmpl"


# Use the syncfs.com certificate
cp "${THIS_DIR}/hpc-reverse-proxy/root/test_certs"/syncfs.com.* "${THIS_DIR}/hpc-reverse-proxy/root/certs"

echo
echo "================================"
echo "    Success!"
echo "    nginx.tmpl has been patched for local test with the syncfs.com domain."
echo "    Don't forget to revert the change before commiting."
echo "================================"
