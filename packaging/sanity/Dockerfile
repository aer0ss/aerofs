FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

# TODO (WW) use bash rather than nc. See container-scripts/service-barrier
RUN apt-get update &&\
    apt-get install -y \
        netcat &&\
    rm -rf /var/lib/apt/lists/*

ADD root/opt/sanity/requirements-exact.txt /

RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        --requirement /requirements-exact.txt &&\
    rm /requirements-exact.txt

EXPOSE 8000

# -u to disable log output buffering
CMD [ "/container-scripts/restart-on-error", "python", "-u", "/opt/sanity/check.py" ]

# Add source files last
COPY root /
