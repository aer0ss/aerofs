FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 80

CMD [ "/run.sh" ]

ENV BUILD_PKGS git cmake gcc g++

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
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
    rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

COPY root/opt/msitools/install_msitools /opt/msitools/install_msitools

RUN /opt/msitools/install_msitools

COPY root/opt/repackaging/api/requirements.txt /

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y gcc &&\
    pip install -r /requirements.txt && rm /requirements.txt &&\
    apt-get purge -y gcc && rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy

# Add source files last
COPY root buildroot /
