FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# TODO (WW) use bash rather than nc. See container-scripts/service-barrier
RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y netcat && rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

ADD buildroot/opt/sanity/requirements-exact.txt /

RUN pip install --requirement /requirements-exact.txt && rm /requirements-exact.txt

EXPOSE 8000

# -u to disable log output buffering
CMD [ "python", "-u", "/opt/sanity/check.py" ]

# Add source files last
COPY root buildroot /
