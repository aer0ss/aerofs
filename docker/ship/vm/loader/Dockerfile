FROM alpine:3.6

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

# Install Docker 17.03. Newer versions may not be supported by all the host environments.
RUN curl -L --fail https://download.docker.com/linux/static/stable/x86_64/docker-17.03.2-ce.tgz -o docker.tgz &&\
    tar -xvf docker.tgz docker/docker &&\
    mv docker/docker /usr/local/bin/docker &&\
    rm docker.tgz &&\
    chmod +x /usr/local/bin/docker

# Install crane
RUN curl --fail -L https://github.com/redbooth/crane/releases/download/v1.5.1/crane_linux_amd64 -o /usr/local/bin/crane &&\
    chmod +x /usr/local/bin/crane

EXPOSE 80

# -u to disable console print buffering
ENTRYPOINT [ "python", "-u", "/main.py" ]

COPY root /

RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker -r /requirements.txt
