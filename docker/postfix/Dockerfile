# TODO: switch to alpine
FROM ubuntu:15.04

MAINTAINER AeroFS "support@aerofs.com"

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

CMD [ "/run.sh" ]

COPY root /
