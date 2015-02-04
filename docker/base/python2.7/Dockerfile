# TODO (WW) use aerofs/base
FROM python:2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

ENTRYPOINT [ "/container-scripts/service-barrier" ]

# requests is needed by aerofs_common.configuration
RUN pip install requests

COPY buildroot /

RUN pip install /sdist/* && rm -rf /sdist
