FROM aerofs/base.jre8-and-mysql-client

ENV TOMCAT_VERSION 6.0.39
ENV TOMCAT_HOME /usr/share/tomcat6

RUN curl --fail https://archive.apache.org/dist/tomcat/tomcat-6/v$TOMCAT_VERSION/bin/apache-tomcat-$TOMCAT_VERSION.tar.gz | \
        tar -xzf - -C /usr/share && \
    mv /usr/share/apache-tomcat-$TOMCAT_VERSION $TOMCAT_HOME && \
    rm -rf $TOMCAT_HOME/webapps/*

CMD [ "/run.sh" ]

EXPOSE 8080

COPY root buildroot /
