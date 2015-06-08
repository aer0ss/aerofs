FROM debian:sid

RUN apt-get update -q &&\
    apt-get install -y \
        curl \
        python

RUN curl -SL https://raw.github.com/pypa/pip/master/contrib/get-pip.py -o /tmp/get-pip.py &&\
    python /tmp/get-pip.py &&\
    rm /tmp/get-pip.py

RUN pip install \
    "devpi-server>=2.0.6,<2.1dev" \
    "requests>=2.4.0,<2.5"

COPY root /

VOLUME ["/var/cache/devpi"]

EXPOSE 80
CMD ["/run.sh"]
