FROM alpine:3.6

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add bash &&\
    rm -rf /var/cache/apk/*

# redis
VOLUME /data

# Used by Repackaging & Nginx
VOLUME /opt/repackaging/installers/modified

# Used by Config
VOLUME /opt/config/properties

# Used by Config for license.gpg
VOLUME /etc/aerofs

# Used by CA
VOLUME /opt/ca/prod

# Used by MySQL
VOLUME /var/lib/mysql

# For maintenance-bunker & bunker to share the backup file.
# TODO (WW) remove this.
VOLUME /opt/bootstrap/public

# Used by onboard SA
VOLUME /aerofs-storage

CMD [ "/run.sh" ]

COPY root /
