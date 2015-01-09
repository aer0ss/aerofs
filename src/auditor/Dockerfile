FROM aerofs/base.jre8

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Use a single RUN statement to minimize image size. Alternatively, run a shell script
# which would be much cleaner.
RUN \
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/auditor &&\
    ln -sf /dev/stdout /var/log/auditor/auditor.log

# Import files
ADD root buildroot /

# Expose ports
EXPOSE 9300

# Define default command
CMD [ "/run.sh" ]
