FROM nginx

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN apt-get update && apt-get install -y openssl curl && rm -rf /var/lib/apt/lists/*

# Import files _after_ apt-get to improve image cache hits
ADD root buildroot /

EXPOSE 80 443 4433

ENTRYPOINT [ "/container-scripts/service-barrier" ]

CMD [ "/run.sh" ]
