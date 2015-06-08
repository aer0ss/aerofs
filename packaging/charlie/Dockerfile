FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 8701

CMD [ "/run.sh" ]

RUN apt-get update && apt-get install -y pypy && rm -rf /var/lib/apt/lists/*

RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        virtualenv &&\
    virtualenv --python /usr/bin/pypy /pythonenv

COPY root buildroot /

RUN /pythonenv/bin/pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        --requirement /opt/charlie/requirements-exact.txt &&\
    /pythonenv/bin/pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        /sdist/* &&\
    rm -rf /sdist
