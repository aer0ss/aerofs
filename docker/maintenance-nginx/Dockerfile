FROM nginx:1.7.11

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y openssl curl && rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

# Import files _after_ apt-get to improve image cache hits
COPY root buildroot /

EXPOSE 80 443

ENTRYPOINT [ "/container-scripts/service-barrier" ]

CMD [ "/run.sh" ]
