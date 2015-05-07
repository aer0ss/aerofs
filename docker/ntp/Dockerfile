FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 80

CMD [ "/run.sh" ]

RUN apt-get update && apt-get install -y ntpdate && rm -rf /var/lib/apt/lists/*

RUN pip install Flask

COPY root /
