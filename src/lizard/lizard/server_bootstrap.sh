#!/bin/bash -e

containers="hpc-monitoring hpc-docker-gen hpc-port-allocator hpc-logrotator hpc-reverse-proxy"

for container in $containers; do
    set +e; docker ps | grep $container; EXIT=$?; set -e
    if [ $EXIT -eq 0 ]
    then
        docker stop $container
        docker rm $container
        docker rmi "registry.aerofs.com/aerofs/$container"
    fi
done

set +e; docker rmi "registry.aerofs.com/aerofs/hpc-sail"; set -e


# Note: this script must be run as root on the HPC server

# Move the certs to the correct location
mkdir -p /hpc/certs && mv /home/core/aerofs.com.crt /home/core/aerofs.com.key /hpc/certs/

# Pull the hpc-sail container so that lizard will be able to use it
docker pull registry.aerofs.com/aerofs/hpc-sail:latest

# Create and run our HPC docker containers:

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

# Monitoring
docker run --detach --restart=always                               \
    --publish=5000:5000                                            \
    --volume=/var/run/docker.sock:/var/run/docker.sock             \
    --volume=/var/hpc-monitoring:/state                            \
    --volume=/home/core/aws_credentials:/aws_credentials           \
    --name=hpc-monitoring                                          \
    registry.aerofs.com/aerofs/hpc-monitoring:latest

echo ""
echo "HPC server configured successfully!"
