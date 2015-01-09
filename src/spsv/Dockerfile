# This generates the base image for all servlet containers

FROM aerofs/base.jre8-and-mysql-client

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN apt-get update && apt-get install -y tomcat6 && rm -rf /var/lib/apt/lists/* &&\
    ln -s /etc/tomcat6 /usr/share/tomcat6/conf

# Import files *after* apt-get to leverage image cache
ADD root buildroot /

# Expose port
EXPOSE 8080

# Define default entry point
CMD [ "/run.sh" ]
