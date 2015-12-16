FROM ubuntu:15.04
MAINTAINER AeroFS "support@aerofs.com"

RUN apt-get update &&\
    apt-get install -y \
        nodejs \
        npm &&\
    rm -rf /var/lib/apt/lists/*

WORKDIR /opt/blurber
ADD root /

RUN cd /opt/blurber && npm install

EXPOSE 8080

CMD ["nodejs", "server.js"]

