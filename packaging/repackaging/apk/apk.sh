#!/bin/bash

THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t apkg - <<EOF
FROM alpine:edge

RUN apk -U upgrade
RUN adduser build -D -G abuild &&\
    echo "build ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers &&\
    mkdir -p /var/cache/distfiles &&\
    chown build:abuild -R /var/cache/distfiles

RUN apk -U add bash alpine-sdk \
    cmake openssl-dev intltool util-linux-dev \
    glib-dev libgsf-dev gobject-introspection-dev

RUN su build -c 'abuild-keygen -a -i -n'

RUN mkdir -p /aports && chown build:abuild -R /aports

RUN su build -c "git clone --depth 1 git://dev.alpinelinux.org/aports"
EOF

pkg=$1
dep=$2
src=${3:-$PWD/$pkg}
dst=${4:-$PWD}

# mapped folders do not reliably reflect changes on the host due to a bug in
# VirtualBox so we need to create a temporary container w/ the source files
# in its build context.
# 
# https://www.virtualbox.org/ticket/9069
# https://github.com/benchflow/client/issues/7
#
# This is specially unfortunate because docker is a stinking pile of shit and
# doesn't allow one to set ownership/permissions of files in the COPY/ADD
# statements. Instead one needs to do an extra RUN step to chown/chmod, which
# creates a brand new layer and thus duplicates all affected files.
cat > $src/.apkg.dockerfile <<EOF
FROM apkg
COPY . /aports/main/$1
RUN chown build:abuild -R /aports/main/$1
EOF
docker build -t apkg-$1 -f $src/.apkg.dockerfile "$src"
rm -f $src/.apkg.dockerfile

docker run --rm -it \
    -v $dst:/home/build/packages/main/ \
    apkg-$1 \
    su build -c "${dep:+"sudo apk -U add $dep --repository /home/build/packages/main &&\\"}
        cd /aports/main/$1 &&\
        abuild checksum &&\
        abuild -r
        "

docker rmi apkg-$1
