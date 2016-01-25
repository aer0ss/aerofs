FROM alpine:3.3
MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add nodejs &&\
    rm -rf /var/cache/apk/*

WORKDIR /opt/blurber
ADD root /

RUN cd /opt/blurber && npm install

EXPOSE 8080

CMD ["node", "server.js"]

