FROM alpine:3.5

RUN apk --update add \
        nginx && \
    rm -rf /var/cache/apk/*

CMD ["nginx", "-g", "daemon off;"]

COPY root /
