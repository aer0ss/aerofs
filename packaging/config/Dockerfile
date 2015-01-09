FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 5434

CMD [ "/run.sh" ]

RUN apt-get update && apt-get install -y libgpgme11 libgpgme11-dev &&\
    rm -rf /var/lib/apt/lists/*

ADD root buildroot /

RUN \
    pip install --requirement /opt/config/requirements-exact.txt &&\
    pip install /sdist/* && rm -rf /sdist &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/aerofs &&\
    ln -sf /dev/stdout /var/log/aerofs/config.log
