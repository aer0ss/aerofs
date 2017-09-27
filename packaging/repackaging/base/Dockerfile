FROM alpine:3.6

MAINTAINER AeroFS "support@aerofs.com"

COPY x86_64/ /cxt/x86_64
RUN apk --update add -X /cxt --allow-untrusted \
        dmg-hfsplus \
        msitools && \
    rm -rf /var/cache/apk/*
