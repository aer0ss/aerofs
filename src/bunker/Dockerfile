FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Expose port
EXPOSE 8484

# Set working dir, or the app would refuse to start
WORKDIR /opt/bunker

CMD [ "/run.sh" ]

# mysql-client for backup scripts, docker for collecting docker logs
RUN apt-get update && apt-get install -y mysql-client docker.io && rm -rf /var/lib/apt/lists/*

COPY buildroot/requirements-exact.txt /

RUN \
    apt-get update && apt-get install -y gcc &&\
    pip install -r /requirements-exact.txt &&\
    apt-get purge -y gcc && rm -rf /var/lib/apt/lists/* &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/bunker &&\
    ln -sf /dev/stdout /var/log/bunker/bunker.log

# Add source files last
COPY root buildroot /

