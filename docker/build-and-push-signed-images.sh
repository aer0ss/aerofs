#!/bin/bash
set -ex

(set +e
    curl ci.arrowfs.org >/dev/null 2>&1
    [[ $? = 0 ]] || (
        echo "ERROR: please connect to VPN"
        exit 22
    )
)

THIS_DIR="$(dirname $0)"

"${THIS_DIR}/../invoke" clean

"${THIS_DIR}/build-images.sh" --signed

"${THIS_DIR}/ship-aerofs/push-images.sh"
