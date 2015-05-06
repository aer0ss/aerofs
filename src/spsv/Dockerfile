FROM aerofs/base.jre8-and-mysql-client

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y tomcat6 &&\
    rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy &&\
    ln -s /etc/tomcat6 /usr/share/tomcat6/conf

CMD [ "/run.sh" ]

EXPOSE 8080

# Add files last
COPY root buildroot /
