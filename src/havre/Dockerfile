FROM aerofs/base.jre8

RUN mkdir -p /var/log/havre && \
    ln -sf /dev/stdout /var/log/havre/havre.log

EXPOSE 8083 8084

CMD [ "/run.sh" ]

COPY root buildroot /
