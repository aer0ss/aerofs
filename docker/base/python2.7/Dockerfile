FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN \
    apt-get update && apt-get install -y python2.7 &&\
    ln -s /usr/bin/python2.7 /usr/bin/python &&\
    # avoid piping, it could swallow curl failure
    curl -SL 'https://bootstrap.pypa.io/get-pip.py' -o /tmp/get-pip.py &&\
    python /tmp/get-pip.py --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker &&\
    rm -f /tmp/get-pip.py &&\
    #
    # Clean up
    #
    find /usr/local \
        \( -type d -a -name test -o -name tests \) \
        -o \( -type f -a -name '*.pyc' -o -name '*.pyo' \) \
        -exec rm -rf '{}' + &&\
    rm -rf /var/lib/apt/lists/*

# requests is needed by aerofs_common.configuration
RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        requests==2.7.0

COPY buildroot /

RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        /sdist/* &&\
    rm -rf /sdist
