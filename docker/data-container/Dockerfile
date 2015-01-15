FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

# TODO (WW) move verkehr's /data/topics to /data/verkehr
RUN mkdir -p \
    /data/bunker \
    /data/web \
    /data/redis

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

# No-op entry point
CMD [ "true" ]
