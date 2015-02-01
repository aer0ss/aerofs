FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 80

CMD [ "/run.sh" ]

RUN apt-get update && apt-get install -y p7zip-full zip &&\
    #
    # Compile libdmg-hfsplus
    #
    apt-get install -y cmake &&\
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
    apt-get purge --auto-remove -y cmake &&\
    rm -rf /var/lib/apt/lists/* 

COPY root/opt/repackaging/api/requirements.txt /

RUN pip install -r /requirements.txt &&\
    rm -rf /sdist && rm /requirements.txt

# Add source files last
COPY root buildroot /
