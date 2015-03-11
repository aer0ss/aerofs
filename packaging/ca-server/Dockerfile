FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Expose port
EXPOSE 9002

# Define default command
CMD [ "/run.sh" ]

RUN apt-get update && apt-get install -y make && rm -rf /var/lib/apt/lists/*

# TODO (WW) move opt to ./root
COPY opt /opt
COPY root /

# PREDOCKER have the service accept external requests
RUN sed -i -e 's/"localhost"/"0.0.0.0"/' /opt/ca/server.py
