FROM aerofs/base

MAINTAINER AeroFS "support@aerofs.com"

RUN apk --update add python py-pip &&\
    rm -rf /var/cache/apk/*

# requests is needed by aerofs_common.configuration
RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        requests==2.7.0

COPY buildroot /

RUN cd /python-lib && python setup.py install && rm -rf build dist
