FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Expose port
EXPOSE 8484

# Set working dir, or the app would refuse to start
WORKDIR /opt/bunker

# Define default command. -u to disable log output buffering
CMD [ "python", "-u", "/opt/bunker/entry.py" ]

# Add files last
ADD root buildroot /

RUN \
    pip install --requirement /opt/bunker/requirements-exact.txt &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/bunker &&\
    ln -sf /dev/stdout /var/log/bunker/bunker.log
