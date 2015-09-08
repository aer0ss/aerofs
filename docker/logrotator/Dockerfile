FROM alpine:3.3

RUN apk --update add \
        logrotate && \
    rm -rf /var/cache/apk/*

CMD [ "/usr/sbin/crond", "-f", "-L", "/dev/stdout" ]

COPY root /
