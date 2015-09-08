FROM alpine:3.3

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add \
        nginx && \
    rm -rf /var/cache/apk/*

CMD ["nginx", "-g", "daemon off;"]

COPY root /
