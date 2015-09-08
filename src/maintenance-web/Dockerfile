FROM aerofs/base.python2.7

EXPOSE 6543

WORKDIR /opt/maintenance-web

CMD [ "/run.sh" ]

COPY root/requirements-exact.txt /
RUN apk --update add \
        py-crypto && \
    pip install \
        --index-url http://devpi.docker/root/pypi/+simple/ \
        --trusted-host devpi.docker \
        -r /requirements-exact.txt && \
    rm -rf /var/cache/apk/*

COPY root buildroot /
