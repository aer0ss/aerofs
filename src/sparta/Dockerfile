FROM aerofs/base.jre8-and-mysql-client

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Use a single RUN statement to minimize image size. Alternatively, run a shell script
# which would be much cleaner.
RUN \
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/sparta &&\
    ln -sf /dev/stdout /var/log/sparta/sparta.log

# Import files
COPY root buildroot /

# Expose ports
EXPOSE 8085 8700

# Define default command
CMD [ "/run.sh" ]

RUN mkdir -p /etc/aerofs && touch /etc/aerofs/private-deployment-flag
