# sid is needed for java8
FROM debian:sid

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

ENTRYPOINT [ "/container-scripts/service-barrier" ]

ADD buildroot /
