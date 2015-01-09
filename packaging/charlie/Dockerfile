FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 8701

CMD [ "/run.sh" ]

RUN apt-get update && apt-get install -y pypy && rm -rf /var/lib/apt/lists/* &&\
    virtualenv --python /usr/bin/pypy /pythonenv

ADD root buildroot /

RUN /pythonenv/bin/pip install --requirement /opt/charlie/requirements-exact.txt &&\
    /pythonenv/bin/pip install /sdist/* &&\
    rm -rf /sdist
