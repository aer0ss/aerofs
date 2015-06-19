FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 5434

CMD [ "/run.sh" ]

ENV BUILD_PKGS gcc python-dev

RUN apt-get update && apt-get install -y \
        libgpgme11 libgpgme11-dev $BUILD_PKGS && \
    rm -rf /var/lib/apt/lists/*

COPY root/opt/config/requirements-exact.txt /opt/config/
COPY buildroot/sdist /sdist/

RUN pip install \
        --index-url http://devpi.docker/root/pypi/+simple/ \
        --trusted-host devpi.docker \
        --requirement /opt/config/requirements-exact.txt && \
    pip install \
        --index-url http://devpi.docker/root/pypi/+simple/ \
        --trusted-host devpi.docker \
        /sdist/* && \
    rm -rf /sdist && \
    apt-get purge -y $BUILD_PKGS && \
    mkdir -p /var/log/aerofs && \
    ln -sf /dev/stdout /var/log/aerofs/config.log

COPY root buildroot /
