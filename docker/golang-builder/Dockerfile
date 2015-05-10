FROM debian:sid

RUN apt-get update &&\
    apt-get install --no-install-recommends -y -q \
        bzr \
        ca-certificates \
        curl \
        git \
        golang \
        mercurial \
        &&\
    mkdir /gopath

ADD builder.sh /builder.sh
RUN chmod +x /builder.sh

ENV GOPATH /gopath
ENV PATH $PATH:$GOPATH/bin

ENTRYPOINT ["/builder.sh"]
