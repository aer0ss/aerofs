FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 80

CMD [ "/run.sh" ]

RUN apk --update add \
        dbus \
        sntpc \
        py-flask &&\
    rm -rf /var/cache/apk/*

COPY root /
