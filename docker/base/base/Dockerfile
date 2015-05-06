# Ubuntu 15 is needed for Java 8
FROM ubuntu:15.04

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y curl vim &&\
    rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

# TODO (WW) remove this
RUN mkdir -p /etc/aerofs && touch /etc/aerofs/private-deployment-flag

ENTRYPOINT [ "/container-scripts/service-barrier" ]

COPY root buildroot /
