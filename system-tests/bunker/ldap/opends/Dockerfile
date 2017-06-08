# For some reason OpenDS doesn't run on Java 7 or 8
FROM java:6-jre

RUN apt-get update && apt-get install -y wget unzip && rm -rf /var/lib/apt/lists/*

RUN wget https://fossies.org/linux/misc/OpenDS-2.2.1.zip -O opends.zip &&\
    unzip opends.zip && rm opends.zip

COPY root /

RUN /configure-opends.sh

EXPOSE 4444 389 636

CMD /OpenDS-2.2.1/bin/start-ds && sleep infinity
