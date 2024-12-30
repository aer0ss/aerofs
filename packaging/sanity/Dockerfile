FROM aerofs/base.python2.7

EXPOSE 8000

# TODO (WW) use bash rather than nc. See container-scripts/service-barrier
RUN apk --update add \
        coreutils \
        netcat-openbsd && \
    rm -rf /var/cache/apk/*

# -u to disable log output buffering
CMD [ "/container-scripts/restart-on-error", "python", "-u", "/opt/sanity/check.py" ]

COPY root/opt/sanity/requirements-exact.txt /
RUN pip install \
        --requirement /requirements-exact.txt && \
    rm /requirements-exact.txt

COPY root /
