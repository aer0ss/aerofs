FROM aerofs/base.python2.7

VOLUME /aerofs-storage

CMD [ "/run.sh" ]

COPY root/requirements-exact.txt /

# temporary compiler infrastructure for pip packages
RUN apk -U add build-base libffi-dev openssl-dev python-dev &&\
    pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        --requirement /requirements-exact.txt &&\
    apk del build-base libffi-dev openssl-dev python-dev &&\
    rm -rf /var/cache/apk/*

COPY root buildroot /

