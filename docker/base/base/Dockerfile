# Ubuntu 15 is needed for Java 8
FROM ubuntu:15.04

MAINTAINER AeroFS "support@aerofs.com"

RUN apt-get update && apt-get install -y curl vim && rm -rf /var/lib/apt/lists/*

ENTRYPOINT [ "/container-scripts/service-barrier" ]

COPY root buildroot /
