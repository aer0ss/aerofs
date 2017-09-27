FROM aerofs/base.jre8

ENV TOMCAT_VERSION 7.0.70
ENV TOMCAT_HOME /usr/share/tomcat7

RUN curl --fail https://archive.apache.org/dist/tomcat/tomcat-7/v$TOMCAT_VERSION/bin/apache-tomcat-$TOMCAT_VERSION.tar.gz | \
        tar -xzf - -C /usr/share && \
    mv /usr/share/apache-tomcat-$TOMCAT_VERSION $TOMCAT_HOME && \
    rm -rf $TOMCAT_HOME/conf/server.xml &&\
    rm -rf $TOMCAT_HOME/conf/web.xml &&\
    rm -rf $TOMCAT_HOME/conf/catalina.properties &&\
    rm -rf $TOMCAT_HOME/webapps/* &&\
    rm -rf $TOMCAT_HOME/lib/jasper*.jar &&\
    rm -rf $TOMCAT_HOME/lib/websocket-api.jar &&\
    rm -rf $TOMCAT_HOME/lib/tomcat7-websocket.jar &&\
    rm -rf $TOMCAT_HOME/lib/tomcat-i18n-*.jar

ENTRYPOINT [ "/run.sh" ]

EXPOSE 8080

COPY root buildroot /
