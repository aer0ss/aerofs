FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

# Expose port
EXPOSE 6543

WORKDIR /opt/maintenance-web

CMD [ "/run.sh" ]

COPY root/requirements-exact.txt /

ENV BUILD_PKGS gcc python-dev

RUN \
    apt-get update && apt-get install -y $BUILD_PKGS &&\
    pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        -r /requirements-exact.txt &&\
    apt-get purge -y $BUILD_PKGS && rm -rf /var/lib/apt/lists/*

# Add source files last
COPY root buildroot /
