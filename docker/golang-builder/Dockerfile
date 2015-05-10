FROM debian:sid

RUN apt-get update &&\
    apt-get install --no-install-recommends -y -q \
        bzr \
        ca-certificates \
        curl \
        docker.io \
        git \
        golang \
        mercurial \
        &&\
    mkdir /gopath

ADD build.sh /build.sh
RUN chmod +x /build.sh

ENV GOPATH /gopath
ENV PATH $PATH:$GOPATH/bin

ENTRYPOINT ["/build.sh"]
