FROM alpine:3.21

RUN apk --update add \
        python3 \
        py3-pip \
        py3-wheel \
        py3-ruamel.yaml \
        && \
    rm -rf /var/cache/apk/*

RUN python3 -m venv .venv &&\
    .venv/bin/pip install "devpi-server<6" ruamel.yaml

ADD server.sh /

EXPOSE 80

CMD ["/server.sh", "/var/cache/devpi"]
