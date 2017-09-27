FROM alpine:3.6

RUN apk --update add \
        postfix && \
    rm -rf /var/cache/apk/*

EXPOSE 25

CMD [ "/run.sh" ]

COPY root /
