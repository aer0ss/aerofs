#!/bin/bash

# Need VPN for gradle to download depenencies
curl -s repos.arrowfs.org/nexus/content/groups/allrepos >/dev/null
[[ $? = 0 ]] || {
    echo "ERROR: please connect to VPN for repos.arrowfs.org access"
    exit 22
}

set -ex

THIS_DIR="$(dirname $0)"

"${THIS_DIR}/../invoke" clean

"${THIS_DIR}/build-images.sh" --signed

"${THIS_DIR}/ship-aerofs/push-images.sh"
