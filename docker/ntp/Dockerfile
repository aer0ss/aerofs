FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 80

CMD [ "/run.sh" ]

RUN apk --update add sntpc &&\
    rm -rf /var/cache/apk/*

RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        Flask

COPY root /
