FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

# Expose port
EXPOSE 8484

# Set working dir, or the app would refuse to start
WORKDIR /opt/bunker

CMD [ "/run.sh" ]

# mysql-client for backup scripts
# py-* to avoid having to pull temporary compiler infra
RUN apk --update add \
        mariadb-client \
        py-crypto \
        py-mysqldb \
        py-psutil &&\
    rm -rf /var/cache/apk/*

COPY root/requirements-exact.txt /

RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        -r /requirements-exact.txt

# Add source files last
COPY root buildroot /

