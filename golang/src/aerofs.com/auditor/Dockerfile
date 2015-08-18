FROM scratch
ADD bin/auditor /auditor
EXPOSE 9300
RUN /var/lib/dpkg/info/ca-certificates-java.postinst configure
CMD ["/auditor"]
