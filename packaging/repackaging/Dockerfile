FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 80

CMD [ "/run.sh" ]

ENV BUILD_PKGS git cmake gcc g++

RUN \
    apt-get update && apt-get install -y p7zip-full zlib1g-dev libbz2-dev python-dev zip fakeroot $BUILD_PKGS &&\
    #
    # Build libdmg-hfsplus
    #
    git clone --branch=bzip2 --depth=1 https://github.com/aerofs/libdmg-hfsplus.git &&\
    cd libdmg-hfsplus &&\
    cmake CMakeLists.txt -DCMAKE_INSTALL_PREFIX=/usr/local/bin &&\
    cd hfs &&\
    make install &&\
    cd ../dmg &&\
    make install &&\
    #
    # Clean up
    #
    cd ../.. && rm -rf libdmg-hfsplus &&\
    apt-get purge --auto-remove -y $BUILD_PKGS &&\
    rm -rf /var/lib/apt/lists/*

COPY buildroot/opt/msitools/install-msitools.sh /opt/msitools/install-msitools.sh

RUN /opt/msitools/install-msitools.sh cleanup-apt

COPY root/opt/repackaging/api/requirements.txt /

RUN \
    apt-get update && apt-get install -y gcc &&\
    pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        -r /requirements.txt &&\
    rm /requirements.txt &&\
    apt-get purge -y gcc && rm -rf /var/lib/apt/lists/*

# Add source files last
COPY root buildroot /
