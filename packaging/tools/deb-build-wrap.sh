#!/bin/bash

set -eu

# OSX 10.11+ comes with System Integrity Protection which basically guts
# the root account. This means, among other annoying things, that fakeroot
# no longer works...
# Use docker to work around this

if [[ $(uname) == "Linux" ]] ; then
    exec $(dirname $0)/deb-build.sh $@
fi

docker build -t debbuild - <<EOF
FROM alpine:3.3
RUN apk --update --upgrade add \
        bash \
        dpkg \
        fakeroot \
        make \
        tar \
        wget &&\
   rm -rf /var/cache/apk/*
EOF

GIT_ROOT=$(git rev-parse --show-toplevel)

# For some weird reason, using $@ directly in the command below does not work
# as the second argument gets lost somehow...
args="$@"
docker run --rm -v $GIT_ROOT:/aerofs debbuild bash \
    -c "cd /aerofs/packaging && ./tools/deb-build.sh $args"
