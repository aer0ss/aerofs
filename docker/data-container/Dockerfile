FROM debian:jessie

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Used by verkehr (/data/topics) and Redis (/data). Other containers
# should use it, too.
VOLUME /data

# Used by Bunker
# TODO (WW) rename all /data2 to data
VOLUME /data2/bunker

# Used by Web
VOLUME /data2/web

# Used by Repackaging & Nginx
VOLUME /opt/repackaging/installers/modified

# Used by Config
VOLUME /opt/config/properties

# Used for license-expired-flag, configuration-initialized-flag
VOLUME /var/aerofs

# Used by Config for license.gpg
VOLUME /etc/aerofs

# Used by Ca
VOLUME /opt/ca/prod

# Used by MySQL
VOLUME /var/lib/mysql

# No-op entry point
CMD [ "/bin/true" ]
