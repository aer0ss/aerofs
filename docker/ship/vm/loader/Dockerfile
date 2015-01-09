FROM python:2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Install crane & docker
RUN apt-get update && apt-get install -y docker.io && rm -rf /var/lib/apt/lists/* &&\
    wget https://github.com/michaelsauter/crane/releases/download/v1.0.0/crane_linux_amd64 -O /usr/local/bin/crane &&\
    chmod 0755 /usr/local/bin/crane

RUN pip install Flask pyyaml

ADD root /

EXPOSE 80

# -u to disable console print buffering
ENTRYPOINT [ "python", "-u", "/run.py" ]
