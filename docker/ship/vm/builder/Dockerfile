FROM python:2.7

MAINTAINER AeroFS "support@aerofs.com"

RUN pip install Jinja2 pyyaml
RUN apt-get update && apt-get install -y qemu-utils && rm -rf /var/lib/apt/lists/*

# See https://coreos.com/security/image-signing-key/ for signing key ID
# TODO: store the signing key in our repo?
ADD https://coreos.com/security/image-signing-key/CoreOS_Image_Signing_Key.asc /coreos.asc

ADD http://stable.release.core-os.net/amd64-usr/current/coreos_production_image.bin.bz2 /coreos.bin.bz2
ADD http://stable.release.core-os.net/amd64-usr/current/coreos_production_image.bin.bz2.sig /coreos.bin.bz2.sig

RUN \
    gpg --import coreos.asc &&\
    gpg --verify coreos.bin.bz2.sig &&\
    echo "Decompression may take a while..." &&\
    bunzip2 /coreos.bin.bz2

COPY root /
