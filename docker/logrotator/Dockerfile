FROM aerofs/base

RUN apt-get update && apt-get install -y logrotate && rm -rf /var/lib/apt/lists/*

COPY root /

CMD [ "/run.sh" ]
