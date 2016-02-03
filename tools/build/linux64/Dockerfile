FROM ubuntu:lucid

ENV DEBIAN_FRONTEND=noninteractive

RUN echo 'deb-src http://archive.ubuntu.com/ubuntu lucid main restricted universe' >> /etc/apt/sources.list

RUN apt-get update && \
    apt-get build-dep -y openssl && \
    apt-get install -y -o DPkg::Options::=--force-confold \
        ant \
        git-core \
        openjdk-6-jdk \
        qt4-qmake \
        swig

ENV JAVA_HOME /usr/lib/jvm/java-6-openjdk

VOLUME /mnt/repos
