FROM aerofs/base

RUN apk --update --upgrade add \
        nginx &&\
    rm -rf /var/cache/apk/*

RUN mkdir -p /run/nginx &&\
    ln -sf /dev/stdout /var/lib/nginx/logs/access.log &&\
    ln -sf /dev/stderr /var/lib/nginx/logs/error.log

# Ensure this folder exists to save us work modifying the image later.
RUN mkdir -p /etc/nginx/sites-disabled

EXPOSE 80 443 4433 5222

ENTRYPOINT [ "/container-scripts/service-barrier" ]
CMD [ "/run.sh" ]

COPY root /
