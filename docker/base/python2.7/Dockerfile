# TODO (WW) use aerofs/base
FROM python:2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

ENTRYPOINT [ "/container-scripts/service-barrier" ]

COPY buildroot /

RUN pip install /sdist/* && rm -rf /sdist
