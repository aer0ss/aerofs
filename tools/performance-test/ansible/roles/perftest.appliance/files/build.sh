#!/bin/bash
set -eu

REPO_DIR=/repos/aerofs
GERRIT_URL=ssh://NewCI@gerrit.arrowfs.org:29418/aerofs

ssh-keyscan -p 29418 gerrit.arrowfs.org >> /root/.ssh/known_hosts

if [ ! -d $REPO_DIR ]
then
    git clone --depth=1 $GERRIT_URL $REPO_DIR
else
    ( cd $REPO_DIR && git reset --hard HEAD && git pull origin master )
fi

# allow us to checkout specific commits
if [ $# -gt 0 ] ; then
    (set +e
        cd $REPO_DIR && git pull origin master --unshallow || true )
    ( cd $REPO_DIR && git checkout $1 )

fi

# starting the cache may sometimes exit 1, better to brute force it into
# working here than break the build process
(set +e
    for i in {1..5}; do $REPO_DIR/tools/cache/start.sh && break || sleep 5; done
)

$REPO_DIR/invoke --unsigned clean proto build_client package_clients build_images

# Modified from dk-reconfig
DEVMAIL=devmail.aerofs.com
echo "Testing connection to ${DEVMAIL} ..."
# VPN is required to access devmail during appliance setup
nc -z ${DEVMAIL} 25 || {
    echo "ERROR: please connect to VPN for ${DEVMAIL} access"
    exit 22
}

$REPO_DIR/docker/dev/dk-destroy.sh
$REPO_DIR/docker/dev/signup-decoder/stop.sh
$REPO_DIR/docker/dev/signup-decoder/start.sh
$REPO_DIR/docker/ci/modify-appliance.sh
