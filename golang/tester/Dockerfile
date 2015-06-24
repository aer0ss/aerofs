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

ENV GOPATH /gopath

ENV PATH $PATH:/gopath/bin

COPY tester/root /
COPY src /gopath/src

ENTRYPOINT [ "/run.sh" ]
