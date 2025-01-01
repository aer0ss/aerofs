FROM alpine:3.6

EXPOSE 21337

RUN apk --update add \
        python \
        py-pip \
        py-flask \
        py-mysqldb && \
    pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker docker-py==1.6.0 && \
    rm -rf /var/cache/apk/*

CMD [ "python", "-u", "/decoder.py" ]

COPY root /
