FROM aerofs/base.python2.7

MAINTAINER AeroFS "support@aerofs.com"

RUN apt-get update && apt-get install -y python python-setuptools \
    python-dev build-essential libssl-dev libffi-dev &&\
    easy_install pip && pip install netifaces requests protobuf pyopenssl

COPY root buildroot /

CMD [ "/run.sh" ]
