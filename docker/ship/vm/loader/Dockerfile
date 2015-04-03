FROM debian:sid

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Install docker. `apt-get install docker.io` doesn't work on sid at the time of writing
RUN apt-get update && apt-get install -y wget &&\
    wget -qO- https://get.docker.com/ | sh &&\
    # Clean up
    apt-get purge -y wget &&\
    apt-get autoremove -y &&\
    rm -rf /var/lib/apt/lists/*

# Build crane
ENV GOPATH /go
ENV CRANE_URL github.com/michaelsauter/crane
ENV CRANE_VERSION v1.1.1
RUN apt-get update && apt-get install -y git golang &&\
    go get -d $CRANE_URL &&\
    cd $GOPATH/src/$CRANE_URL &&\
    git checkout $CRANE_VERSION &&\
    cd / &&\
    go build -o /usr/local/bin/crane $CRANE_URL &&\
    # Clean up
    rm -r $GOPATH &&\
    apt-get purge -y git golang &&\
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
