FROM aerofs/base

RUN apt-get update && apt-get install -y netcat && rm -rf /var/lib/apt/lists/*

EXPOSE 222

CMD [ "nc", "-l", "-p", "222" ]