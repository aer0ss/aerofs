#!/bin/bash
# A script that TeamCity calls to import the OVA.  It's important because the
# last line of /var/log/newci/import_ova.log is used to figure out the
# appliance's IP for DNS purposes
set -e -x
set -o pipefail

SCRIPT_DIR=$(dirname $0)
CHECKOUT_DIR="$SCRIPT_DIR/../.."
OVA_PATH="$CHECKOUT_DIR/packaging/bakery/private-deployment/images/aerofs-appliance.ova"

$SCRIPT_DIR/import_ova.sh "$OVA_PATH" eth0 | tee /var/log/newci/import_ova.log
