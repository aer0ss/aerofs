FROM alpine:3.3

RUN apk --update add \
        python \
        py-flask \
        py-mysqldb &&\
    rm -rf /var/cache/apk/*

COPY root /

CMD [ "python", "-u", "/decoder.py" ]

EXPOSE 21337
