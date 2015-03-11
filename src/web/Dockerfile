FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Expose port
EXPOSE 6543

# Set working dir, or the app would refuse to start
WORKDIR /opt/web

CMD [ "/run.sh" ]

COPY requirements-exact.txt /

RUN \
    apt-get update && apt-get install -y gcc &&\
    pip install --requirement /requirements-exact.txt &&\
    rm /requirements-exact.txt &&\
    apt-get purge -y gcc && rm -rf /var/lib/apt/lists/* &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/web &&\
    ln -s /dev/stdout /var/log/web/web.log

# Add source files last
COPY root buildroot /
