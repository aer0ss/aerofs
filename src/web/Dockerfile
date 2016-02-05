FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

COPY root/requirements-exact.txt /

RUN apk -U add --virtual deps \
        build-base \
        git \
        python-dev \
        libqrencode-dev &&\
    git clone https://github.com/aerofs/fastqrcode.git &&\
    cd fastqrcode &&\
    python setup.py install &&\
    cd .. &&\
    rm -rf fastqrcode &&\
    apk del deps &&\
    apk add libqrencode &&\
    rm -rf /var/cache/apk/*

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

COPY root buildroot /

EXPOSE 6543

WORKDIR /opt/web

CMD [ "/run.sh" ]

