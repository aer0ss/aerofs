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

# Install Docker 1.5. Newer versions may not be supported by all the host environments.
# The code was adapted from https://get.docker.com
RUN apt-get install -y apt-transport-https ca-certificates &&\
    apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9 &&\
    echo deb https://get.docker.com/ubuntu docker main > /etc/apt/sources.list.d/docker.list &&\
    apt-get update &&\
    apt-get install -y lxc-docker-1.5.0


COPY root /

ENV GOPATH /gopath
ENV PATH $PATH:$GOPATH/bin

ENTRYPOINT ["/builder.sh"]
