FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 5434

CMD [ "/run.sh" ]

COPY root buildroot /

ENV BUILD_PKGS gcc python-dev

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y libgpgme11 libgpgme11-dev $BUILD_PKGS &&\
    pip install --requirement /opt/config/requirements-exact.txt &&\
    pip install /sdist/* && rm -rf /sdist &&\
    apt-get purge -y $BUILD_PKGS && rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/aerofs &&\
    ln -sf /dev/stdout /var/log/aerofs/config.log
