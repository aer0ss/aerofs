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
    inetutils-ping \
    jq \
    libmysqlclient-dev \
    libgpgme11-dev \
    mono-devel \
    netcat \
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
    protobuf==2.6.0 \
    requests \
    virtualenv \
    python-swiftclient

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

# Install Docker 1.5. Newer versions may not be supported by all the host environments.
# The code was adapted from https://get.docker.com
RUN apt-get install -y apt-transport-https ca-certificates &&\
    apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9 &&\
    echo deb https://get.docker.com/ubuntu docker main > /etc/apt/sources.list.d/docker.list &&\
    apt-get update &&\
    apt-get install -y lxc-docker-1.5.0

# Build libmdg-hfsplus
RUN apt-get install -y cmake p7zip-full zlib1g-dev libbz2-dev fakeroot &&\
    git clone --branch=bzip2 --depth=1 https://github.com/aerofs/libdmg-hfsplus.git &&\
    cd libdmg-hfsplus &&\
    cmake CMakeLists.txt -DCMAKE_INSTALL_PREFIX=/usr/local/bin &&\
    cd hfs &&\
    make install &&\
    cd ../dmg &&\
    make install &&\
    cd ../.. && rm -rf libdmg-hfsplus

# Build makensis
#
# NSIS provides the "LogSet on" instruction to automatically log all events to an install.log file.
# Unfortunately, in order to use this feature, makensis must be manually compiled with a special flag.

# Note: NSIS releases are versionned in the form x.xx. As of this writing, we're using the 2.46 release,
# published in 2010. Check [on the Sourceforge page](http://sourceforge.net/projects/nsis/files/NSIS%202/)
# for newer version.
#
RUN apt-get install -y scons gcc-4.9-multilib g++-4.9-multilib &&\
    mkdir /usr/local/nsis && cd /usr/local/nsis &&\
    wget http://downloads.sourceforge.net/project/nsis/NSIS%202/2.46/nsis-2.46-src.tar.bz2 &&\
    wget http://downloads.sourceforge.net/project/nsis/NSIS%202/2.46/nsis-2.46-log.zip &&\
    wget http://downloads.sourceforge.net/project/nsis/NSIS%202/2.46/nsis-2.46.zip &&\
    #
    # Patch and build makensis
    #
    tar jxf nsis-2.46-src.tar.bz2 &&\
    unzip nsis-2.46.zip && cd nsis-2.46-src &&\
    wget https://raw.githubusercontent.com/tpokorra/lbs-nsis/master/nsis/gcc46NameLookupChanges.patch &&\
    patch SCons/Config/gnu gcc46NameLookupChanges.patch &&\
    scons NSIS_CONFIG_LOG=yes SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA=no \
        PREFIX=/usr/local/nsis/nsis-2.46 install-compiler &&\
    ln -s /usr/local/nsis/nsis-2.46/bin/makensis /usr/local/bin &&\
    #
    # Symlink magic
    #
    cd /usr/local/nsis/nsis-2.46 &&\
    mkdir share && cd share &&\
    ln -s /usr/local/nsis/nsis-2.46 nsis &&\
    #
    # Replace default stub
    #
    cd /usr/local/nsis/nsis-2.46 &&\
    mv Stubs Stubs-no-log &&\
    cd .. &&\
    unzip nsis-2.46-log.zip &&\
    rm makensis.exe &&\
    mv Stubs nsis-2.46

# Build msibuild
COPY root/scripts/install-msitools.sh /scripts/install-msitools.sh
RUN /scripts/install-msitools.sh

ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/jre

# For TeamCity agent
EXPOSE 9090

# Needed by the 'invoke' script
ENV USER root

ADD root /

# git sometimes reverts the permission of this file
RUN chmod 400 /root/.ssh/id_rsa
