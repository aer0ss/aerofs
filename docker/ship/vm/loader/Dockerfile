FROM alpine:3.3

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add \
        bash \
        curl \
        python \
        py-pip \
        py-flask \
        py-jinja2 \
        py-psutil \
        py-requests \
        py-yaml &&\
    rm -rf /var/cache/apk/*

# Pin docker to 1.5.0 otherwise some base CoreOS images might not have the docker
# daemon with an API recent enough to support us.
RUN curl --fail -L https://get.docker.com/builds/Linux/x86_64/docker-1.5.0 -o /usr/local/bin/docker &&\
    chmod +x /usr/local/bin/docker

# Install crane
RUN curl --fail -L https://github.com/redbooth/crane/releases/download/v1.5.1/crane_linux_amd64 -o /usr/local/bin/crane &&\
    chmod +x /usr/local/bin/crane

EXPOSE 80

# -u to disable console print buffering
ENTRYPOINT [ "python", "-u", "/main.py" ]

COPY root /

RUN pip install -r /requirements.txt
