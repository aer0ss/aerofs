FROM alpine:3.3

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add \
        bash \
        curl && \
    rm -rf /var/cache/apk/*

# Pin docker to 1.8.3 otherwise. Current HPC Server CoreOS images have docker-1.8.3.
# Running apk add docker above will download docker-1.9.1 in this container creating
# client/server mismatches. TODO(DS): Think about upgrading CoreOS images for our HPC servers.
RUN curl -L https://get.docker.com/builds/Linux/x86_64/docker-1.8.3 -o /usr/local/bin/docker && \
    chmod +x /usr/local/bin/docker

ENTRYPOINT [ "/sail.sh" ]

COPY sail.sh /
