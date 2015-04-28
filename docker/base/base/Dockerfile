# Ubuntu 15 is needed for Java 8
FROM ubuntu:15.04

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# TODO (WW) remove this
RUN mkdir -p /etc/aerofs && touch /etc/aerofs/private-deployment-flag

ENTRYPOINT [ "/container-scripts/service-barrier" ]

COPY root buildroot /
