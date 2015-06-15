# Linux-specific instructions

This assume you already have your SSH key added to AeroFS servers, and your VPN package.

## Before

You may want to add `MAKEFLAGS="-j4"` to your `/etc/environment`, this will allow you to distribute the
work of `make` on 4 CPU (adjust to your number of cores).

## Packages needed

    sudo apt-get install curl git openjdk-8-jdk python-pip \
                network-manager-openvpn gradle virtualbox qt4-qmake \
                autoconf hfsprogs npm nodejs-legacy
    sudo npm install -g less minifier uglify-js

The following steps are based on the `tools/agent/Dockerfile` file which may evolve.
For a desktop installation, the steps are however slightly different.

    cd ~/repos/
    # Install protobuf
    wget https://github.com/google/protobuf/releases/download/v2.6.0/protobuf-2.6.0.tar.bz2 &&\
    tar jxf protobuf-2.6.0.tar.bz2 &&\
    cd protobuf-2.6.0 &&\
    ./configure &&\
    make -j4 &&\
    sudo make -j4 install &&\
    sudo ldconfig &&\
    cd - && rm -rvf protobuf*

    # Install protobuf Obj-C
    git clone --depth=1 https://github.com/aerofs/protobuf-objc &&\
    cd protobuf-objc &&\
    ./autogen.sh &&\
    ./configure &&\
    make -j4 &&\
    sudo make -j4 install &&\
    cd - && rm -r protobuf-objc

    # If this doesn't work, you may have to install the package libprotobuf-dev or libprotoc-dev

    # Build libmdg-hfsplus
    sudo apt-get install -y cmake p7zip-full zlib1g-dev libbz2-dev fakeroot &&\
    git clone --depth=1 https://github.com/aerofs/libdmg-hfsplus.git &&\
    cd libdmg-hfsplus &&\
    cmake CMakeLists.txt -DCMAKE_INSTALL_PREFIX=/usr/local &&\
    cd hfs &&\
    make -j4 &&\
    sudo make install &&\
    cd ../dmg &&\
    make -j4 &&\
    sudo make install &&\
    cd ../.. && rm -rf libdmg-hfsplus

    # Now the funny part, installing makensis
    sudo apt-get install -y scons gcc-4.9-multilib g++-4.9-multilib &&\
    sudo mkdir /usr/local/nsis && cd /usr/local/nsis &&\
    sudo wget http://downloads.sourceforge.net/project/nsis/NSIS%202/2.46/nsis-2.46-src.tar.bz2 &&\
    sudo wget http://downloads.sourceforge.net/project/nsis/NSIS%202/2.46/nsis-2.46-log.zip &&\
    sudo wget http://downloads.sourceforge.net/project/nsis/NSIS%202/2.46/nsis-2.46.zip &&\
    #
    # Patch and build makensis
    #
    sudo tar jxf nsis-2.46-src.tar.bz2 &&\
    sudo unzip nsis-2.46.zip && cd nsis-2.46-src &&\
    sudo wget https://raw.githubusercontent.com/tpokorra/lbs-nsis/master/nsis/gcc46NameLookupChanges.patch &&\
    sudo patch SCons/Config/gnu gcc46NameLookupChanges.patch &&\
    sudo scons NSIS_CONFIG_LOG=yes SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA=no \
        PREFIX=/usr/local/nsis/nsis-2.46 install-compiler &&\
    sudo ln -s /usr/local/nsis/nsis-2.46/bin/makensis /usr/local/bin &&\
    #
    # Symlink magic
    #
    cd /usr/local/nsis/nsis-2.46 &&\
    sudo mkdir share && cd share &&\
    sudo ln -s /usr/local/nsis/nsis-2.46 nsis &&\
    #
    # Replace default stub
    #
    cd /usr/local/nsis/nsis-2.46 &&\
    sudo mv Stubs Stubs-no-log &&\
    cd .. &&\
    sudo unzip nsis-2.46-log.zip &&\
    sudo rm makensis.exe &&\
    sudo mv Stubs nsis-2.46

    # Now msibuild... lucky boy, there is already a script!
    sudo tools/install-msitools.sh

## VPN

NetworkManager > Create new Connection > Import VPN Settings from file. Select the `.conf` file from the package.

## Environment configuration

    # As root
    echo 'JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/' >> /etc/environment

## Let's go

    dk-create-vm
    dk-create
