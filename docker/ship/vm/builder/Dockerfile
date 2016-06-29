FROM alpine:3.3

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add \
        bash \
        curl \
        gnupg \
        py-jinja2 \
        py-yaml \
        python \
        qemu-img \
        util-linux

# See https://coreos.com/security/image-signing-key/ for signing key ID
# TODO: store the signing key in our repo?
ADD https://coreos.com/security/image-signing-key/CoreOS_Image_Signing_Key.asc /coreos.asc

# use "current" to track latest stable
ENV COREOS_VERSION 1010.6.0

RUN gpg --import coreos.asc && \
    curl http://stable.release.core-os.net/amd64-usr/$COREOS_VERSION/coreos_production_image.bin.bz2 -o /coreos.bin.bz2 && \
    curl http://stable.release.core-os.net/amd64-usr/$COREOS_VERSION/coreos_production_image.bin.bz2.sig -o /coreos.bin.bz2.sig && \
    gpg --verify coreos.bin.bz2.sig && \
    echo "Decompression may take a while..." && \
    bunzip2 /coreos.bin.bz2

COPY root /
