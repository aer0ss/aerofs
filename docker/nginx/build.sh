#!/bin/bash
#
# Sigh... this is really convoluted but it's the only reliable way
# to workaround docker's misguided refusal to make the build context
# available to RUN statements
#

THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

IMAGE=nginx:aerofs

if "$THIS_DIR/../../tools/cache/img_fresh.sh" $IMAGE $THIS_DIR ; then
    exit 0
fi

# step 1: get local data into container image
TMP=$THIS_DIR/apk/Dockerfile.tmp
tee "$TMP" <<EOF
FROM alpine:3.3
RUN apk --update upgrade
ADD x86_64 /apk/x86_64
EOF
docker build -t nginx-cxt -f "$TMP" "$THIS_DIR/apk/"
rm -f "$TMP"

# step 2: get data from image to volume
docker run --name=nginx-cxt -v /cxt nginx-cxt cp -R /apk/x86_64 /cxt/

# step 3: use data from volume in command
docker run --name=nginx-build \
    --volumes-from nginx-cxt \
    aerofs/base \
    apk -U add nginx --repository /cxt --allow-untrusted

# step 4: commit resulting layer
docker commit nginx-build $IMAGE

docker build -t $IMAGE - <<EOF
FROM $IMAGE
RUN mkdir -p /run/nginx &&\
    ln -sf /dev/stdout /var/lib/nginx/logs/access.log &&\
    ln -sf /dev/stderr /var/lib/nginx/logs/error.log
EOF

docker rm -f nginx-build
docker rm -f nginx-cxt
docker rmi nginx-cxt
