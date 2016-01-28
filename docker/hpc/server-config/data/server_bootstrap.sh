#!/bin/bash -e

# Note: this script must be run as root on the HPC server

# Move the certs to the correct location
mkdir -p /hpc/certs && mv /home/core/aerofs.com.crt /home/core/aerofs.com.key /hpc/certs/

# Create the repo file
echo 'registry.aerofs.com' > /hpc/repo

# Create the tag file. This file contains the version number of the AeroFS appliance that we will run.
# In an ideal world, this file should be per-deployment, but for now we force all deployments on a single
# server to run on the same version, to make things easier.
TAG=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock $(cat /hpc/repo)/aerofs/loader tag)
echo ${TAG} > /hpc/tag

# Pull the Docker images we will be using to speed up the launch of the first deployment
IMAGES=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock $(cat /hpc/repo)/aerofs/loader images)
for i in ${IMAGES}; do IMAGE="$(cat /hpc/repo)/${i}:$(cat /hpc/tag)" && docker pull "${IMAGE}"; done

# Run our HPC containers

# Port allocator
docker run --detach --restart=always                               \
    --volume=/var/hpc-port-allocator:/state                        \
    --name=hpc-port-allocator                                      \
    registry.aerofs.com/aerofs/hpc-port-allocator:latest

# nginx reverse proxy
docker run --detach --restart=always                               \
    --publish=80:80                                                \
    --publish=443:443                                              \
    --volume=/tmp/hpc-reverse-proxy:/etc/nginx/conf.d              \
    --volume=/hpc/certs:/etc/nginx/certs                           \
    --name=hpc-reverse-proxy                                       \
    registry.aerofs.com/aerofs/hpc-reverse-proxy:latest

# docker-gen
docker run --detach --restart=always                               \
    --volumes-from=hpc-reverse-proxy                               \
    --volume=/var/run/docker.sock:/tmp/docker.sock:ro              \
    --name=hpc-docker-gen                                          \
    registry.aerofs.com/aerofs/hpc-docker-gen:latest

# logrotator
docker run --detach --restart=always                               \
    --volume=/var/lib/docker/containers:/var/lib/docker/containers \
    --name=hpc-logrotator                                          \
    registry.aerofs.com/aerofs/hpc-logrotator:latest


echo ""
echo "HPC server configured successfully!"
