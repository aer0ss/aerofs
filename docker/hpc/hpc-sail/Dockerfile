FROM alpine:3.3

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add \
        bash \
        docker \
    && rm -rf /var/cache/apk/*

COPY sail.sh /

ENTRYPOINT [ "/sail.sh" ]
