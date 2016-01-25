FROM alpine:3.3

RUN apk --update add python py-pip py-requests &&\
    pip install "devpi-server==2.5.2" &&\
    rm -rf /var/cache/apk/*

EXPOSE 80
CMD ["devpi-server", "--host", "0.0.0.0", "--port", "80", "--serverdir", "/var/cache/devpi"]
