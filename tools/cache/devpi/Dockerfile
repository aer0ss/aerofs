FROM alpine:3.5

RUN apk --update add \
        python \
        py-pip \
        py-requests && \
    pip install "devpi-server==4.0.0" && \
    rm -rf /var/cache/apk/*

EXPOSE 80

CMD ["devpi-server", "--host", "0.0.0.0", "--port", "80", "--serverdir", "/var/cache/devpi"]
