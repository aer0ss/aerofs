FROM python:alpine

MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 80

# -u to disable console print buffering
ENTRYPOINT [ "python", "-u", "/main.py" ]

ADD root /
RUN pip install -r /requirements.txt
