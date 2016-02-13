#!/bin/bash
#
# Sigh... this is really convoluted but it's the only reliable way
# to workaround docker's misguided refusal to make the build context
# available to RUN statements
#

THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# $1 image
# $2 source path
# return true if the image is newer than the newest file in the source tree
function newer() {
    if [[ $(uname -s) == "Darwin" ]] ; then
        # sigh...
        #  1. docker CLI is not flexible enough so we need to use API directly
        #  2. OSX is a broken piece of shit, SecureTransport can't load PEM cert/key so we need to
        #     convert them to PKCS12
        if [[ ! -f $DOCKER_CERT_PATH/client.p12 ]] ; then
            openssl pkcs12 -export -in $DOCKER_CERT_PATH/cert.pem -inkey $DOCKER_CERT_PATH/key.pem \
                -out $DOCKER_CERT_PATH/client.p12 -passout pass:.
        fi

        curl_opts="-k -E $DOCKER_CERT_PATH/client.p12:. https${DOCKER_HOST#tcp}"
        stat_format="-f %m"
    elif [[ $(uname -s) == "Linux" ]] ; then
        curl_opts="--unix-socket /var/run/docker.sock http:"
        stat_format="-c %Y"
    else
        echo "unsupported platform: always rebuild"
    fi

    if [[ -n "$stat_format" ]] ; then
        # find newest timestamp in entire source tree
        newest=$(find $2 -type f | xargs stat $stat_format | sort -nr | head -n 1)

        url_image=$(python -c "import urllib; print urllib.quote('''$1''')")
        # find creation date of container, if present
        # NB: remove sub-second precision and convert from ISO 8601 to Unix epoch
        created=$(curl --fail $curl_opts/images/$url_image/json 2>/dev/null | jq -r '.Created[0:19]+"Z" | fromdate')

        echo "newest : $newest"
        echo "created: $created"
    
        [[ -n "$created" ]] && (( "$created" > "$newest" ))
    else
        true
    fi
}

IMAGE=nginx:aerofs

if newer $IMAGE $THIS_DIR/apk/x86_64 ; then
    exit 0
fi

# step 1: get local data into container image
TMP=$THIS_DIR/apk/Dockerfile.tmp
tee "$TMP" <<EOF
FROM alpine:3.3
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

docker rm -f nginx-build
docker rm -f nginx-cxt
docker rmi nginx-cxt

