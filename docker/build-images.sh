#!/bin/bash
set -e

#
# Build all AeroFS images. Sort non-base image names alphabetically to help users observe build progress
#

DIRS="
    base/base
    base/jre8
    base/jre8-and-mysql-client
    base/python2.7

    aeroim
    ../golang/src/aerofs.com/analytics
    ../golang/src/aerofs.com/auditor
    ../src/blurber
    ../src/bunker
    ../golang/src/aerofs.com/ca-server
    ../packaging/config
    ../golang/src/aerofs.com/charlie
    data-container
    enforcer
    ../src/havre
    ../golang/src/github.com/aerofs/lipwig
    logrotator
    maintenance-nginx
    ../src/maintenance-web
    mysql
    nginx
    ntp
    postfix
    ../src/polaris
    redis
    ../packaging/repackaging
    ../packaging/sanity
    ../golang/src/aerofs.com/sloth
    ../src/sparta
    ../src/spsv
    ../src/trifrost
    ../golang/src/aerofs.com/ts-probe
    ../golang/src/aerofs.com/valkyrie
    ../src/web

    ship-aerofs/loader
"

PWD="$(dirname "$0")"

$PWD/../tools/cache/start.sh

for i in ${DIRS}; do
    echo "========================================"
    echo "  Building Docker image ${i}"
    echo "========================================"
    make -C "$PWD/${i}" image
done

echo "Removing untagged images to save space..."
docker rmi `docker images --no-trunc | grep '^<none>' | awk '{print $3}'` 2>/dev/null || true
