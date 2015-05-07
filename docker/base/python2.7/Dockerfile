FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN \
    apt-get update && apt-get install -y python2.7 &&\
    ln -s /usr/bin/python2.7 /usr/bin/python &&\
    curl -SL 'https://bootstrap.pypa.io/get-pip.py' | python &&\
    #
    # Clean up
    #
    find /usr/local \
        \( -type d -a -name test -o -name tests \) \
        -o \( -type f -a -name '*.pyc' -o -name '*.pyo' \) \
        -exec rm -rf '{}' + &&\
    rm -rf /var/lib/apt/lists/*

# requests is needed by aerofs_common.configuration
RUN pip install requests

COPY buildroot /

RUN pip install /sdist/* && rm -rf /sdist
