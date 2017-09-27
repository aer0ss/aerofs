FROM aerofs/base.jre8

RUN mkdir -p /var/log/sparta && \
    ln -sf /dev/stdout /var/log/sparta/sparta.log

EXPOSE 8085 8700

ENTRYPOINT [ "/run.sh" ]

COPY root buildroot /
