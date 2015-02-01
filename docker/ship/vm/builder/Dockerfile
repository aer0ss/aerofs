FROM python:2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Combine download and unzip so we don't pollute Docker build cache
# TODO (WW) verify the downloaded image
RUN \
    wget http://alpha.release.core-os.net/amd64-usr/current/coreos_production_image.bin.bz2 -O /coreos.bin.bz2 &&\
    echo "Decompression may take a while..." &&\
    bunzip2 /coreos.bin.bz2

RUN pip install Jinja2 pyyaml
RUN apt-get update && apt-get install -y qemu-utils && rm -rf /var/lib/apt/lists/*

COPY root /
