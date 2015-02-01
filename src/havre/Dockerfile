FROM aerofs/base.jre8

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Import files
COPY root buildroot /

# Use a single RUN statement to minimize image size. Alternatively, run a shell script
# which would be much cleaner.
RUN \
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/havre &&\
    ln -sf /dev/stdout /var/log/havre/havre.log

# Expose port
EXPOSE 8083 8084

# Define default command
CMD [ "/run.sh" ]
