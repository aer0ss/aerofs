FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 5434

CMD [ "/run.sh" ]

RUN apt-get update && apt-get install -y libgpgme11 libgpgme11-dev &&\
    rm -rf /var/lib/apt/lists/*

COPY root buildroot /

ENV BUILD_PKGS gcc python-dev

RUN \
    apt-get update && apt-get install -y $BUILD_PKGS &&\
    pip install --requirement /opt/config/requirements-exact.txt &&\
    pip install /sdist/* && rm -rf /sdist &&\
    apt-get purge -y $BUILD_PKGS && rm -rf /var/lib/apt/lists/* &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/aerofs &&\
    ln -sf /dev/stdout /var/log/aerofs/config.log
