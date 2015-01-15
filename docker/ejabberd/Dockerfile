FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN apt-get update &&\
    DEBIAN_FRONTEND=noninteractive apt-get install -y ejabberd mysql-client &&\
    rm -rf /var/lib/apt/lists/* &&\
    # We'll supply with our own pem
    rm -rf /etc/ejabberd/ejabberd.pem

CMD [ "/run.sh" ]

EXPOSE 5222

ADD root buildroot /
