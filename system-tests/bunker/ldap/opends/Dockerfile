# For some reason OpenDS doesn't run on Java 7 or 8
FROM java:6-jre

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y wget unzip && rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

RUN wget http://java.net/downloads/opends/promoted-builds/2.2.1/OpenDS-2.2.1.zip -O opends.zip &&\
    unzip opends.zip && rm opends.zip

COPY root /

RUN /configure-opends.sh

EXPOSE 4444 389

CMD /OpenDS-2.2.1/bin/start-ds && sleep infinity
