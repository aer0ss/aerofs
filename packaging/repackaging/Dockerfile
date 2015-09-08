FROM aerofs/repackaging-base

EXPOSE 80

RUN apk --update add \
        bash \
        curl \
        dpkg \
        fakeroot \
        p7zip \
        py-flask \
        py-pip \
        py-psutil \
        py-requests \
        python \
        tar \
        util-linux \
        xz \
        zip && \
    rm -rf /var/cache/apk/*

ENTRYPOINT [ "/container-scripts/service-barrier" ]
CMD [ "/run.sh" ]

COPY buildroot root /
