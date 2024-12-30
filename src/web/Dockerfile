FROM aerofs/base.python2.7

RUN apk --update add --virtual deps \
        build-base \
        git \
        python-dev \
        libqrencode-dev && \
    git clone https://github.com/aerofs/fastqrcode.git && \
    cd fastqrcode && \
    python setup.py install && \
    cd .. && \
    rm -rf fastqrcode && \
    apk del deps && \
    apk add libqrencode && \
    rm -rf /var/cache/apk/*

EXPOSE 6543

WORKDIR /opt/web

CMD [ "/run.sh" ]

COPY root/requirements-exact.txt /
RUN apk --update add \
        py-crypto \
        py-pillow && \
    pip install \
        --requirement /requirements-exact.txt && \
    rm -rf /var/cache/apk/* && \
    mkdir -p /var/log/web && \
    ln -s /dev/stdout /var/log/web/web.log

COPY root buildroot /
