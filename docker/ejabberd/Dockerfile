FROM aerofs/base

MAINTAINER AeroFS "support@aerofs.com"

# Python is for the auth_all script. MySQL client is for run.sh.
RUN apt-get update &&\
    apt-get install -y ejabberd mysql-client python2.7 &&\
    rm -rf /var/lib/apt/lists/* &&\
    ln -s /usr/bin/python2.7 /usr/bin/python &&\
    # We'll supply with our own pem
    rm -rf /etc/ejabberd/ejabberd.pem

CMD [ "/run.sh" ]

EXPOSE 5222

COPY root /
