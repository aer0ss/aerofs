FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Expose port
EXPOSE 8484

# Set working dir, or the app would refuse to start
WORKDIR /opt/bunker

CMD [ "/run.sh" ]

# mysql-client for backup scripts, docker for collecting docker logs
RUN apt-get update && apt-get install -y mysql-client docker.io libmysqlclient-dev && rm -rf /var/lib/apt/lists/*

COPY buildroot/requirements-exact.txt /

ENV BUILD_PKGS gcc python-dev

RUN \
    apt-get update && apt-get install -y $BUILD_PKGS &&\
    pip install -r /requirements-exact.txt &&\
    apt-get purge -y $BUILD_PKGS && rm -rf /var/lib/apt/lists/*

# Add source files last
COPY root buildroot /

