FROM alpine:3.21

RUN apk --update add \
        nginx nginx-mod-stream && \
    rm -rf /var/cache/apk/*

CMD ["nginx", "-g", "daemon off;"]

COPY root /
