FROM nginx:1.9.9

MAINTAINER AeroFS "support@aerofs.com"

RUN apt-get update && \
    apt-get install -y openssl curl && \
    rm -rf /var/lib/apt/lists/*

# Ensure this folder exists to save us work modifying the image later.
RUN mkdir -p /etc/nginx/sites-disabled

# Import files _after_ apt-get to improve image cache hits
COPY root buildroot /

EXPOSE 80 443 4433 5222

ENTRYPOINT [ "/container-scripts/service-barrier" ]

CMD [ "/run.sh" ]
