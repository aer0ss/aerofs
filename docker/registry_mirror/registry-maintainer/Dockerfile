FROM alpine:3.3

RUN apk --update add \
        bash \
        curl && \
    rm -rf /var/cache/apk/*

RUN curl -L https://get.docker.com/builds/Linux/x86_64/docker-1.8.3 -o /usr/local/bin/docker &&\
    chmod +x /usr/local/bin/docker

RUN (crontab -l ; echo "0 3 * * 6 /pull_push.sh")| crontab -

CMD ["/run.sh"]

COPY root /

