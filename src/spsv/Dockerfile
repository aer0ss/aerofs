FROM aerofs/base.jre8-and-mysql-client

MAINTAINER AeroFS "support@aerofs.com"

RUN apt-get update && apt-get install -y tomcat6 &&\
    rm -rf /var/lib/apt/lists/* &&\
    ln -s /etc/tomcat6 /usr/share/tomcat6/conf

CMD [ "/run.sh" ]

EXPOSE 8080

# Add files last
COPY root buildroot /
