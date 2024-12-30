FROM aerofs/base

RUN apk --update add \
        python \
        py-pip && \
    rm -rf /var/cache/apk/*

ADD pip.conf /etc/pip.conf
ADD pip.conf ~/.pydistutils.cfg

# requests is needed by aerofs_common.configuration
RUN pip install \
        wheel requests==2.7.0

COPY buildroot /

RUN cd /python-lib && \
    pip install google-apputils &&\
    pip install -r requirements-exact.txt &&\
    python setup.py install && \
    rm -rf build dist
