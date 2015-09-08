FROM aerofs/base

EXPOSE 6379

RUN apk --update add \
        redis && \
    rm -rf /var/cache/apk/*

VOLUME /data
WORKDIR /data

CMD ["/container-scripts/restart-on-error", "redis-server", "--appendonly", "yes"]
