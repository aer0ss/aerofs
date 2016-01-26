FROM alpine:3.3

RUN apk --update add \
        python \
        py-pip \
        py-flask \
        py-mysqldb &&\
    pip install dnspython &&\
    rm -rf /var/cache/apk/*

COPY root /

CMD [ "python", "-u", "/decoder.py" ]

EXPOSE 21337
