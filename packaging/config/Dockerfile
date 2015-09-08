FROM aerofs/base.python2.7

RUN apk --update add \
        gpgme-dev && \
    rm -rf /var/cache/apk/*

COPY root/opt/config/requirements-exact.txt /opt/config/
COPY buildroot/licensing /licensing

RUN cd /licensing &&\
    apk --update add --virtual build-deps \
        build-base \
        python-dev && \
    pip install \
        --index-url http://devpi.docker/root/pypi/+simple/ \
        --trusted-host devpi.docker \
        --requirement /opt/config/requirements-exact.txt && \
    python setup.py install && \
    apk del build-deps && \
    rm -rf /var/cache/apk/* && \
    mkdir -p /var/log/aerofs && \
    ln -sf /dev/stdout /var/log/aerofs/config.log

EXPOSE 5434

CMD [ "/run.sh" ]

COPY root /
