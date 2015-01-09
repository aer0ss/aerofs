FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Import files. TODO (WW) move opt to ./root
ADD opt /opt
ADD root /

# PREDOCKER have the service accept external requests
RUN sed -i -e 's/"localhost"/"0.0.0.0"/' /opt/ca/server.py

# Expose port
EXPOSE 9002

# Define default command
CMD [ "/run.sh" ]
