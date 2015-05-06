FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 8701

CMD [ "/run.sh" ]

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y pypy && rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

RUN pip install virtualenv && virtualenv --python /usr/bin/pypy /pythonenv

COPY root buildroot /

RUN /pythonenv/bin/pip install --requirement /opt/charlie/requirements-exact.txt &&\
    /pythonenv/bin/pip install /sdist/* &&\
    rm -rf /sdist
