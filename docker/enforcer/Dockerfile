FROM alpine:3.3

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add bash curl &&\
    rm -rf /var/cache/apk/*

CMD [ "/run.sh" ]

COPY root /
