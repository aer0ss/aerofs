# Ubuntu14 ships ejabberd v2 while Debian and Ubuntu15 ships v14. v2 has a different configuration format
# than v14 and our legacy production uses v2.
FROM aerofs/base.ubuntu14.04

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Python is for the auth_all script. MySQL client is for run.sh.
RUN apt-get update &&\
    apt-get install -y ejabberd=2.1.11-1ubuntu2.1 mysql-client python2.7 &&\
    rm -rf /var/lib/apt/lists/* &&\
    ln -s /usr/bin/python2.7 /usr/bin/python &&\
    # We'll supply with our own pem
    rm -rf /etc/ejabberd/ejabberd.pem

CMD [ "/run.sh" ]

EXPOSE 5222

COPY root buildroot /
