FROM alpine:3.3

MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 5000

RUN apk --update add \
       bash \
       py-pip \
       py-psutil \
       python \
       python-dev && \
    rm -rf /var/cache/apk/*

ADD root /

RUN pip install -r /requirements.txt

ENTRYPOINT ["python", "-u", "/main.py"]
