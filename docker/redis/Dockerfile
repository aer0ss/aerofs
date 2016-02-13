FROM aerofs/base

RUN apk -U add redis &&\
    rm -rf /var/cache/apk/*

VOLUME /data
WORKDIR /data

EXPOSE 6379

CMD ["/container-scripts/restart-on-error", "redis-server", "--appendonly", "yes"]

