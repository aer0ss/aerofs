FROM alpine:3.3

RUN apk --update add nginx &&\
    rm -rf /var/cache/apk/*

COPY root /

CMD ["nginx", "-g", "daemon off;"]
