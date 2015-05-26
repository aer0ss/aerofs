#!/bin/bash
set -e
#
# Build all AeroFS images from ground up. See `invoke` for command-line options
#
# Sort non-base image names alphabetically to help users observe build progress

DIRS="
    golang-builder

    base/base
    base/jre8
    base/jre8-and-mysql-client
    base/python2.7

    ../src/auditor 
    ../src/bunker 
    ../packaging/config 
    ../packaging/charlie 
    data-container 
    enforcer 
    ejabberd 
    ../src/havre 
    logrotator
    maintenance-nginx 
    mysql 
    nginx 
    ntp 
    postfix 
    ../src/polaris 
    redis 
    ../packaging/repackaging 
    ../packaging/sanity 
    ../src/sparta 
    
    ../src/spsv 
    ../src/spsv/resources/openid
    ../src/spsv/resources/log-collection 
    ../src/spsv/resources/sp 
    ../src/spsv/resources/verification 
    
    ../src/verkehr 
    ../src/web 
    ../src/zephyr 
    
    ../golang/src/aerofs.com/ca-server
    ../golang/src/aerofs.com/ts-probe

    ship/vm/loader 
    ship-aerofs/loader
"

THIS_DIR="$(dirname $0)"

echo "Building client packages..."
"${THIS_DIR}/../invoke" proto build_client package_clients $@

for i in ${DIRS}; do
    echo "========================================"
    echo "  Building Docker image ${i}"
    echo "========================================"
    make -C "${THIS_DIR}/${i}" image
done

echo "Removing untagged images to save space..."
docker rmi `docker images --no-trunc | grep '^<none>' | awk '{print $3}'` 2>/dev/null || true
