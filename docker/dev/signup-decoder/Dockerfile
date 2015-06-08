FROM ubuntu:15.04

RUN apt-get update && apt-get install -y \
        curl \
        python \
        docker.io &&\
    curl -SL 'https://bootstrap.pypa.io/get-pip.py' | python \
        --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker &&\
    pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        Flask

COPY root /

CMD [ "python", "-u", "/decoder.py" ]

EXPOSE 21337
