FROM debian:sid

RUN apt-get update &&\
    apt-get install --no-install-recommends -y -q \
        bzr \
        ca-certificates \
        curl \
        git \
        mercurial \
        patch

# Install Docker 17.03. Newer versions may not be supported by all the host environments.
RUN curl -L --fail https://download.docker.com/linux/static/stable/x86_64/docker-17.03.2-ce.tgz -o docker.tgz &&\
    tar -xvf docker.tgz docker/docker &&\
    mv docker/docker /usr/local/bin/docker &&\
    rm docker.tgz &&\
    chmod +x /usr/local/bin/docker

# Installing from official packages makes it easier to bump Go version when distro lags
# behind. For instance Debian is taking too long to upgrade to 1.5.3 which includes an
# important security fix.
RUN curl https://storage.googleapis.com/golang/go1.5.3.linux-amd64.tar.gz -o go.tar.gz &&\
    tar -C /usr/local -xzf go.tar.gz &&\
    rm -rf go.tar.gz &&\
    mkdir /gopath

ENV GOPATH /gopath

ENV PATH $PATH:/usr/local/go/bin:/gopath/bin

COPY root /

ENTRYPOINT [ "/run.sh" ]
