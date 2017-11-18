FROM i386/ubuntu:trusty

ENV DEBIAN_FRONTEND=noninteractive

RUN echo 'deb-src http://archive.ubuntu.com/ubuntu trusty main restricted universe' >> /etc/apt/sources.list

RUN apt-get update && \
    apt-get build-dep -y openssl && \
    apt-get install -y -o DPkg::Options::=--force-confold \
        ant \
        git-core \
        openjdk-6-jdk \
        qt4-qmake \
        swig \
        libxml2-utils \
        libglib2.0-bin \
        libgtk-3-dev \
        libgdk-pixbuf2.0-dev

ENV JAVA_HOME /usr/lib/jvm/java-6-openjdk

VOLUME /mnt/repos
