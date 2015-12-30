FROM ubuntu:15.04

MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 80

CMD [ "/run.sh" ]

ENV BUILD_PKGS git cmake gcc g++

RUN \
    apt-get update &&\
    apt-get install -y curl python2.7 &&\
    ln -s /usr/bin/python2.7 /usr/bin/python &&\
    curl -SL 'https://bootstrap.pypa.io/get-pip.py' -o /tmp/get-pip.py &&\
    python /tmp/get-pip.py --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker &&\
    rm -f /tmp/get-pip.py &&\
    find /usr/local \
        \( -type d -a -name test -o -name tests \) \
        -o \( -type f -a -name '*.pyc' -o -name '*.pyo' \) \
        -exec rm -rf '{}' + &&\
    #
    apt-get install -y p7zip-full zlib1g-dev libbz2-dev python-dev zip fakeroot $BUILD_PKGS &&\
    #
    # Build libdmg-hfsplus
    #
    git clone --depth=1 https://github.com/aerofs/libdmg-hfsplus.git &&\
    cd libdmg-hfsplus &&\
    cmake CMakeLists.txt -DCMAKE_INSTALL_PREFIX=/usr/local &&\
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

COPY root/opt/msitools/install-msitools.sh /opt/msitools/

# TODO: embed to avoid redundant install/remove of temp deps
RUN /opt/msitools/install-msitools.sh cleanup-apt

COPY root/opt/repackaging/api/requirements.txt /

RUN pip install --index-url http://devpi.docker/root/pypi/+simple/ --trusted-host devpi.docker \
        -r /requirements.txt

# Add source files last
COPY root buildroot /

ENTRYPOINT [ "/container-scripts/service-barrier" ]
