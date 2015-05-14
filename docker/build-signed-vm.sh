#!/bin/bash

echo "Testing connection to repos.arrowfs.org ..."
# Need VPN for gradle to download depenencies
curl -s repos.arrowfs.org/nexus/content/groups/allrepos >/dev/null
[[ $? = 0 ]] || {
    echo "ERROR: please connect to VPN for repos.arrowfs.org access"
    exit 22
}

set -e

THIS_DIR="$(dirname $0)"

"${THIS_DIR}/../invoke" clean

"${THIS_DIR}/build-images.sh" --signed

"${THIS_DIR}/ship-aerofs/build-vm.sh"
