FROM aerofs/base.jre8-and-mysql-client

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Use a single RUN statement to minimize image size. Alternatively, run a shell script
# which would be much cleaner.
RUN \
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/bifrost &&\
    ln -sf /dev/stdout /var/log/bifrost/bifrost.log

# Import files
ADD root buildroot /

# Expose port
EXPOSE 8700

# Define default command
CMD [ "/run.sh" ]
