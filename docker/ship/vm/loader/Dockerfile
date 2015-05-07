FROM debian:sid

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Install docker. Deian's stock docker.io package is quite out of date.
# Pin it to version 1.5.0 otherwise some customers' base CoreOS images might
# not have the Docker daemon with an API recent enough to support us.
# The following steps are adapted from https://get.docker.com.
RUN \
    apt-get update &&\
    apt-get install -y apt-transport-https ca-certificates &&\
    apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9 &&\
    echo deb https://get.docker.com/ubuntu docker main > /etc/apt/sources.list.d/docker.list &&\
    apt-get update &&\
    apt-get install -y lxc-docker-1.5.0 &&\
    rm -rf /var/lib/apt/lists/*

RUN \
    apt-get update &&\
    apt-get install -y wget &&\
    wget https://github.com/michaelsauter/crane/releases/download/v1.1.1/crane_linux_amd64 -O /usr/local/bin/crane &&\
    chmod 0755 /usr/local/bin/crane &&\
    apt-get purge -y wget &&\
    apt-get autoremove -y &&\
    rm -rf /var/lib/apt/lists/*

# Install python and its packages. Partially adapted from
# https://github.com/docker-library/python/blob/master/2.7/Dockerfile
RUN apt-get update && apt-get install -y python curl &&\
    curl -SL 'https://bootstrap.pypa.io/get-pip.py' | python &&\
    pip install Flask pyyaml requests &&\
    # Clean up
    find /usr/local \
            \( -type d -a -name test -o -name tests \) \
            -o \( -type f -a -name '*.pyc' -o -name '*.pyo' \) \
            -exec rm -rf '{}' + &&\
    apt-get purge -y curl &&\
    apt-get autoremove -y &&\
    rm -rf /var/lib/apt/lists/*

EXPOSE 80

# -u to disable console print buffering
ENTRYPOINT [ "python", "-u", "/main.py" ]

COPY root /
