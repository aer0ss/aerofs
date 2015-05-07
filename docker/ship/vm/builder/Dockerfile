FROM python:2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN pip install Jinja2 pyyaml
RUN apt-get update && apt-get install -y qemu-utils && rm -rf /var/lib/apt/lists/*

ENV COREOS_TAR_BALL http://stable.release.core-os.net/amd64-usr/current/coreos_production_image.bin.bz2

RUN \
    wget $COREOS_TAR_BALL -O /coreos.bin.bz2 &&\
    wget $COREOS_TAR_BALL.sig -O /coreos.bin.bz2.sig &&\
    # See https://coreos.com/security/image-signing-key/ for signing key ID
    gpg --keyserver keys.gnupg.net --recv-keys 50E0885593D2DCB4 &&\
    gpg --verify coreos.bin.bz2.sig &&\
    echo "Decompression may take a while..." &&\
    bunzip2 /coreos.bin.bz2

COPY root /
