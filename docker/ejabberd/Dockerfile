FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

# Python is for the auth_all script. MySQL client is for run.sh.
RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update &&\
    apt-get install -y ejabberd mysql-client python2.7 &&\
    rm -rf /var/lib/apt/lists/* &&\
    ln -s /usr/bin/python2.7 /usr/bin/python &&\
    # We'll supply with our own pem
    rm -rf /etc/ejabberd/ejabberd.pem &&\
    rm /etc/apt/apt.conf.d/80httpproxy

CMD [ "/run.sh" ]

EXPOSE 5222

COPY root /
