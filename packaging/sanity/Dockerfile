FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

# TODO (WW) use bash rather than nc. See container-scripts/service-barrier
RUN apt-get update && apt-get install -y netcat && rm -rf /var/lib/apt/lists/*

ADD root buildroot /

RUN pip install --requirement /opt/sanity/requirements-exact.txt

EXPOSE 8000

# -u to disable log output buffering
CMD [ "python", "-u", "/opt/sanity/check.py" ]
