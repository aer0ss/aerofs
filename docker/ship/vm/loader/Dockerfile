FROM python:2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Install docker
RUN apt-get update && apt-get install -y docker.io && rm -rf /var/lib/apt/lists/*

ENV GOPATH /go
ENV CRANE_URL github.com/michaelsauter/crane
ENV CRANE_VERSION v1.1.1

# Build crane
RUN apt-get update && apt-get install -y git golang &&\
    go get -d $CRANE_URL &&\
    cd $GOPATH/src/$CRANE_URL &&\
    git checkout $CRANE_VERSION &&\
    cd / &&\
    go build -o /usr/local/bin/crane $CRANE_URL &&\
    # Clean up
    rm -r $GOPATH &&\
    apt-get purge -y git golang &&\
    rm -rf /var/lib/apt/lists/*


RUN pip install Flask pyyaml

COPY root /

EXPOSE 80

# -u to disable console print buffering
ENTRYPOINT [ "python", "-u", "/run.py" ]
