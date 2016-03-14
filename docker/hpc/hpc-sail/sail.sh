#!/bin/bash -e

#
# This script follows the pattern of the "sail" script from ship-enterprise. It configures and
# starts a new loader container for HPC. It creates the 'repo', 'tag' and 'target' files on the
# Docker host that the loader container needs, then run the loader.
#
# See: ~/repos/aerofs/docker/ship/vm/builder/root/resources/cloud-config.yml.jinja
# As well as https://github.com/aerofs/aerofs-docker/blob/master/cloud-config.yml
#
# To test this script locally:
# make && docker run --rm -v /hpc/deployments/:/hpc/deployments/ -v /var/run/docker.sock:/var/run/docker.sock aerofs/hpc-sail foobar


if [ $# -ne 1 ]; then
    echo "Usage: $0 <subdomain>"
    exit 1
fi

REPO='registry.aerofs.com'
SUBDOMAIN=$1
DIR=/hpc/deployments/$SUBDOMAIN

# Get the tag of the latest loader from registry
TAG=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock $REPO/aerofs/loader:latest tag)

# Write out the repo, tag and target files
mkdir -p $DIR
echo $REPO > $DIR/repo
echo $TAG > $DIR/tag
echo 'maintenance' > $DIR/target

# Run the loader

# Note: the pattern that we use for the loader name is important as this is how the loader determines
# whether it's running on HPC or not. Therefore, this must be kept in sync with ~/repos/aerofs/docker/ship/vm/loader/root/common.py
NAME="$SUBDOMAIN-hpc-loader-$TAG"
IMAGE="$REPO/aerofs/loader:$TAG"

docker run --detach                                           \
    --name $NAME                                              \
    --restart=always                                          \
    --link=hpc-port-allocator:hpc-port-allocator.service      \
    -v /var/run/docker.sock:/var/run/docker.sock              \
    -v $DIR/repo:/repo                                        \
    -v $DIR/tag:/tag                                          \
    -v $DIR/target:/target                                    \
    "${IMAGE}"                                                \
    load /repo /tag /target