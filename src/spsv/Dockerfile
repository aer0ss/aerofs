# This generates the base image for all servlet containers
#
# We can't use aerofs/base as tomcat6 has been removed from debian:sid.
# TODO (WW) upgrade to tomcat7 or kill tomcat entirely, and use aeofs/base
#
FROM debian:jessie

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN apt-get update && apt-get install -y \
        curl \
        mysql-client \
        tomcat6 \
    && rm -rf /var/lib/apt/lists/* &&\
    ln -s /etc/tomcat6 /usr/share/tomcat6/conf

ENTRYPOINT [ "/container-scripts/service-barrier" ]

CMD [ "/run.sh" ]

EXPOSE 8080

# Add files last
ADD root buildroot /
