FROM aerofs/base

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y netcat && rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

EXPOSE 222

CMD [ "nc", "-l", "-p", "222" ]
