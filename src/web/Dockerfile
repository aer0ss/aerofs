FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Expose port
EXPOSE 6543

# Set working dir, or the app would refuse to start
WORKDIR /opt/web

CMD [ "/run.sh" ]

ADD root buildroot /

RUN \
    pip install --requirement /opt/web/requirements-exact.txt &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/web &&\
    ln -s /dev/stdout /var/log/web/web.log
