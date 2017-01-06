FROM aerofs/base

EXPOSE 6379

RUN apk --update add \
        redis && \
    rm -rf /var/cache/apk/*

VOLUME /data
WORKDIR /data

COPY fix-cache-and-restart-on-error /

CMD ["/fix-cache-and-restart-on-error", "redis-server", "--appendonly", "yes"]
