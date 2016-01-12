FROM python:alpine

MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 80

ADD root /

RUN pip install -r /requirements.txt

# -u to disable console print buffering
ENTRYPOINT [ "python", "-u", "/main.py" ]
