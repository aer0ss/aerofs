#!/bin/bash
set -eu

REPO_DIR=/repos/aerofs

# Assuming the script runs in the CI agent container, and the appliance
# containers run on the same host.
# The appliance IP is then the host's bridge IP.
IP=$(ip route show 0.0.0.0/0 | awk '{print $3}')

$REPO_DIR/tools/wait-for-url.sh share.syncfs.com

docker exec config sed -i "s/open_signup=.*$//g" "/opt/config/properties/external.properties"
docker exec config sh -c "/bin/echo \"open_signup=true\" >> /opt/config/properties/external.properties"

$REPO_DIR/system-tests/bunker/setup/test.sh share.syncfs.com true --add-host share.syncfs.com:172.17.42.1
echo 'Services are up and running.'

$REPO_DIR/invoke --product=CLIENT setupenv
