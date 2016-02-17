FROM alpine:3.3

RUN apk -U add postfix &&\
    rm -rf /var/cache/apk/*

COPY root /

EXPOSE 25

CMD [ "/run.sh" ]
