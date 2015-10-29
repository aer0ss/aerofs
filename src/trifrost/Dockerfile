FROM aerofs/base.jre8-and-mysql-client

MAINTAINER AeroFS "support@aerofs.com"

COPY root buildroot /
RUN /var/lib/dpkg/info/ca-certificates-java.postinst configure

EXPOSE 8888

CMD [ "/run.sh" ]
