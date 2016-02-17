#!/bin/bash
#
# Sigh... this is really convoluted but it's the only reliable way
# to workaround docker's misguided refusal to make the build context
# available to RUN statements
#

trap 'exit 1' ERR

THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ $# -lt 4 ] ; then
    echo "Usage: $0 <img> <path/to/package/repo> <base_image> packages..."
    exit 1
fi

img=$1
repo=$2
base=$3
pkgs="${@:4}"

echo repo: $repo
echo base: $base
echo pkgs: $pkgs
prefix=pkg-insert

# step 1: get local data into container image
TMP=$repo/.dockerfile.tmp
tee "$TMP" <<EOF
FROM alpine:3.3
ADD x86_64 /x86_64
EOF
docker build -t $prefix-cxt -f "$TMP" "$repo/"
rm -f "$TMP"

# step 2: get data from image to volume
docker run --name=$prefix-cxt -v /cxt $prefix-cxt cp -R /x86_64 /cxt/

echo "installing $pkgs"
# step 3: use data from volume in command
docker run --name=$prefix-build \
    --volumes-from $prefix-cxt \
    $base \
    sh -c "echo installing $pkgs && apk add -U -X /cxt --allow-untrusted $pkgs && rm -rf /var/cache/apk/*"

# step 4: commit resulting layer
docker commit $prefix-build $img

# cleanup
docker rm -fv $prefix-build
docker rm -fv $prefix-cxt
docker rmi $prefix-cxt

