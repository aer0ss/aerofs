FROM alpine:3.6

RUN adduser build -D -G abuild &&\
    echo "build ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers &&\
    mkdir -p /var/cache/distfiles &&\
    chown build:abuild -R /var/cache/distfiles

RUN apk --update upgrade && \
    apk add \
        alpine-sdk \
        bash \
        cmake \
        glib-dev \
        gobject-introspection-dev \
        intltool \
        libgsf-dev \
        openssl-dev \
        util-linux-dev && \
    rm -rf /var/cache/apk/*

RUN git clone --depth 1 git://git.alpinelinux.org/aports /aports

COPY dmg-hfsplus /aports/main/dmg-hfsplus
COPY gcab /aports/main/gcab
COPY msitools /aports/main/msitools

RUN chown -R build:abuild /aports

USER build

RUN abuild-keygen -ain
