FROM aerofs/base.python2.7

VOLUME /aerofs-storage

RUN apk --update add py-openssl py-cryptography py-cffi &&\
        rm -rf /var/cache/apk/*

CMD [ "/run.sh" ]

COPY root/requirements-exact.txt /

RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        --requirement /requirements-exact.txt

COPY root /

