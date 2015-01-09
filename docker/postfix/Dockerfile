# Use the same base image as python:2.7 to leverage the image cache.
FROM buildpack-deps:jessie

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN apt-get update &&\
    # Preconfigure postfix and suppress its interactive dialogs during apt-get.
    echo "postfix postfix/mailname string localhost" | debconf-set-selections &&\
    echo "postfix postfix/main_mailer_type string 'Internet Site'" | debconf-set-selections &&\
    DEBIAN_FRONTEND=noninteractive apt-get install -y postfix &&\
    rm -rf /var/lib/apt/lists/*

# Trust the world and relay all their emails
RUN sed -i -e 's`mynetworks =.*`mynetworks = 0.0.0.0/0`' /etc/postfix/main.cf

# Expose port
EXPOSE 25

# The default command. TODO (WW) run postfix or tail its logs in the foreground
CMD /etc/init.d/postfix start && echo "postfix started in the background." && sleep infinity
