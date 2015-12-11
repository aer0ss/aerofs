FROM debian:sid

RUN apt-get update \
    && DEBIAN_FRONTEND="noninteractive" apt-get install --no-install-recommends -y -q \
        bzr \
        ca-certificates \
        curl \
        expect \
        git \
        golang-go \
        mercurial \
        mysql-server \
    && mysql_install_db --user=mysql -ldata=/var/lib/mysql \
    && mkdir /gopath

ENV GOPATH /gopath

ENV PATH $PATH:/gopath/bin

COPY tester/root /
COPY src /gopath/src

ENTRYPOINT [ "/run.sh" ]
