FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

# Expose port
EXPOSE 6543

# Set working dir, or the app would refuse to start
WORKDIR /opt/web

CMD [ "/run.sh" ]

COPY root/requirements-exact.txt /

RUN \
    apk --update add \
        py-crypto \
        py-pillow &&\
    pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        --requirement /requirements-exact.txt &&\
    rm -rf /var/cache/apk/* &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/web &&\
    ln -s /dev/stdout /var/log/web/web.log

# Add source files last
COPY root buildroot /
