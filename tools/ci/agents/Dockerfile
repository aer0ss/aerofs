FROM ubuntu:15.04

########
# Install system packages
#

# Set MySQL root password
RUN \
    echo 'mysql-server mysql-server/root_password password temp123' | debconf-set-selections &&\
    echo 'mysql-server mysql-server/root_password_again password temp123' | debconf-set-selections

RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    debhelper \
    devscripts \
    dh-autoreconf \
    dstat \
    ejabberd \
    git \
    gradle \
    hfsprogs \
    htop \
    libmysqlclient-dev \
    libgpgme11-dev \
    mono-devel \
    nodejs \
    npm \
    openjdk-8-jdk \
    python \
    python-dev \
    python-setuptools \
    qt5-default \
    redis-server \
    mysql-server \
    sed \
    sshpass \
    unzip \
    vim \
    wget \
    zip

# Populate /etc/ssl/certs/java/cacerts
RUN \
    /var/lib/dpkg/info/ca-certificates-java.postinst configure &&\
    update-ca-certificates

RUN ln -s /usr/bin/nodejs /usr/bin/node

RUN npm install -g \
    less \
    minifier \
    uglify-js

RUN easy_install pip

RUN pip install \
    boto \
    pyyaml \
    protobuf \
    requests \
    virtualenv

# Install protobuf
RUN wget https://github.com/google/protobuf/releases/download/v2.6.0/protobuf-2.6.0.tar.bz2 &&\
    tar jxf protobuf-2.6.0.tar.bz2 &&\
    cd protobuf-2.6.0 &&\
    ./configure &&\
    make -j2 install &&\
    ldconfig &&\
    cd - && rm -r protobuf*

# Install protobuf Obj-C
RUN git clone --depth=1 https://github.com/aerofs/protobuf-objc &&\
    cd protobuf-objc &&\
    ./autogen.sh &&\
    ./configure &&\
    make -j2 install &&\
    cd - && rm -r protobuf-objc

ENV TC_AGENT_HOME /teamcity-agent

# Download TeamCity Agent. The IP points to the TeamCity master.
RUN \
    wget --no-check-certificate https://172.19.10.154/update/buildAgent.zip &&\
    mkdir $TC_AGENT_HOME &&\
    cd $TC_AGENT_HOME &&\
    unzip ../buildAgent.zip &&\
    chmod +x bin/*.sh &&\
    rm ../buildAgent.zip

# Configure TeamCity Agent. Hardcode agent name as "cloud-1" for now.
RUN \
    sed -e 's!^serverUrl=.*!serverUrl=https://ci.arrowfs.org/!' \
        -e 's!^name=.*!name=cloud-1!' $TC_AGENT_HOME/conf/buildAgent.dist.properties \
        > $TC_AGENT_HOME/conf/buildAgent.properties

EXPOSE 9090

# Needed by the 'invoke' script
ENV USER root

ADD root /

RUN chmod 400 /root/.ssh/id_rsa
