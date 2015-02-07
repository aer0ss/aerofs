FROM ubuntu:15.04

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

ENTRYPOINT [ "/container-scripts/service-barrier" ]

COPY buildroot /
