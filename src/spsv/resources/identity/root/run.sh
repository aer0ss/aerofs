#!/bin/bash
set -e

AUTHENTICATOR="$(/container-scripts/get-config-property lib.authenticator)"

echo "authenticator: $AUTHENTICATOR"

if [[ $AUTHENTICATOR == "SAML" ]] || [[ $AUTHENTICATOR == "OPENID" ]] ; then
    /run-tomcat.sh
else
    # open a port to keep status checks happy
    echo "dummy TCP listener"
    nc -k -d -l 8080 &>/dev/null
fi
