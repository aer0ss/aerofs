FROM aerofs/base.jre8

MAINTAINER Weihan Wang "weihan@aerofs.com"

RUN \
    #
    # PREDOCKER forward logs to docker log collector
    #
    mkdir -p /var/log/zephyr &&\
    ln -sf /dev/stdout /var/log/zephyr/zephyr.log

EXPOSE 8888

CMD [ "/run.sh" ]

COPY root buildroot /
