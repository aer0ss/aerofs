FROM aerofs/base.python2.7

EXPOSE 8484

WORKDIR /opt/bunker

CMD [ "/run.sh" ]

# mysql-client for backup scripts
# py-* to avoid having to pull temporary compiler infra
RUN apk --update add \
        mariadb-client \
        py-crypto \
        py-mysqldb \
        py-psutil \
        tar &&\
    rm -rf /var/cache/apk/*

COPY root/requirements-exact.txt /

# temporary compiler infra for cryptography and cffi
RUN apk -U add build-base libffi-dev openssl-dev python-dev &&\
    pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        --requirement /requirements-exact.txt &&\
    apk del build-base libffi-dev openssl-dev python-dev &&\
    rm -rf /var/cache/apk/*

COPY root buildroot /
