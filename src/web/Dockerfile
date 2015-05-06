FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Expose port
EXPOSE 6543

# Set working dir, or the app would refuse to start
WORKDIR /opt/web

CMD [ "/run.sh" ]

COPY requirements-exact.txt /

ENV BUILD_PKGS gcc python-dev

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y $BUILD_PKGS &&\
    pip install --requirement /requirements-exact.txt &&\
    rm /requirements-exact.txt &&\
    apt-get purge -y $BUILD_PKGS && rm -rf /var/lib/apt/lists/* &&\
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/web &&\
    rm /etc/apt/apt.conf.d/80httpproxy &&\
    ln -s /dev/stdout /var/log/web/web.log

# Add source files last
COPY root buildroot /
