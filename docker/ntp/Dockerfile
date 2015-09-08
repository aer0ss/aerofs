FROM aerofs/base.python2.7

EXPOSE 80

CMD [ "/run.sh" ]

RUN apk --update add \
        dbus \
        py-flask \
        sntpc && \
    rm -rf /var/cache/apk/*

COPY root /
