FROM aerofs/base.python2.7

MAINTAINER Weihan Wang "weihan@aerofs.com"

EXPOSE 80

CMD [ "/run.sh" ]

RUN apt-get update && apt-get install -y p7zip-full zip &&\
    #
    # Compile libdmg-hfsplus
    #
    apt-get install -y cmake &&\
    git clone --branch=bzip2 https://github.com/aerofs/libdmg-hfsplus.git &&\
    cd libdmg-hfsplus &&\
    # Pin to a specific commit which was the current master by the time of writing.
    git checkout a9284e7919c48099b928a6956b23bb07dba6bd80 &&\
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

ADD root buildroot /

RUN pip install -r /opt/repackaging/api/requirements.txt &&\
    rm -rf /sdist
