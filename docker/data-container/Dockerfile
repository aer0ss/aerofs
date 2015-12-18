FROM alpine:3.2

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add bash

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

CMD [ "/run.sh" ]

COPY root /
