FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Expose port
EXPOSE 8484

# Set working dir, or the app would refuse to start
WORKDIR /opt/bunker

CMD [ "/run.sh" ]

COPY buildroot/requirements-exact.txt /
ENV BUILD_PKGS gcc python-dev

# mysql-client for backup scripts, docker for collecting docker logs
RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y mysql-client docker.io libmysqlclient-dev $BUILD_PKGS &&\
    pip install -r /requirements-exact.txt &&\
    apt-get purge -y $BUILD_PKGS &&\
    rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

# Add source files last
COPY root buildroot /

