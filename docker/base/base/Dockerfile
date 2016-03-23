FROM alpine:edge

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update upgrade && \
    apk add \
        bash \
        curl \
        openssl && \
    rm -rf /var/cache/apk/*

ENTRYPOINT [ "/container-scripts/service-barrier" ]

COPY root /
