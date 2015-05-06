FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
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
    rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy &&\
    # requests is needed by aerofs_common.configuration
    pip install requests==2.4.3

COPY buildroot /

RUN pip install /sdist/* && rm -rf /sdist
