Building OpenJDK 8 for OS X
===========================


Pre-requisites
--------------

- Mac OS X 10.7 and Xcode >=4.5.2 (in particular, we need llvm-gcc)
  - OpenJDK's configure script requires a gcc backend (rules out clang)
  - OpenJDK's source uses Apple's Blocks extension for objc (rules out homebrew gcc-4.8)
  - Takeaway: use llvm-gcc.
- Oracle's Java 7 to serve as a bootstrap JDK

OpenJDK 8 refuses to build with Xcode 5 or newer. There is an ongoing effort to
make OpenJDK 9 compatible with recent clang (which is the default C compiler in
recent Xcode versions) and it will probably be backported to OpenJDK 8 at some
point but for now, Mavericks and Yosemite users need to download Xcode 4.6.3
and install it alongside the newer Xcode (e.g. in /Applications/Xcode4.app)


Building
--------

Instructions are at https://wiki.openjdk.java.net/display/MacOSXPort/Main

# Get the code

    hg clone --pull http://hg.openjdk.java.net/jdk8u/jdk8u openjdk8u
    cd openjdk8u
    bash get_source.sh

# Checkout a known release tag

    bash ./make/scripts/hgforest.sh update -c jdk8u60-b04

# Build

    # Berkeley DB 5 confuses OpenJDK, so make sure it's not
    # available in /usr/local/ at configure/build time
    brew list db > /dev/null
    if [ $? -eq 0 ] ; then
        HIDE_BDB=true
    else
        HIDE_BDB=false
    fi
    $HIDE_BDB && brew unlink db
    export CC=llvm-gcc
    export CXX=llvm-g++
    bash ./configure --with-xcode-path=/Applications/Xcode4.app
    make JOBS=8 images
    $HIDE_BDB && brew link db

Packaging
---------

# Use our tool to remove unnecessary files

    git clone https://github.com/aerofs/openjdk-trim.git
    cd openjdk-trim
    ./jdk-trim osx ../openjdk8u/build/macosx-x86_64-normal-server-release/images/j2sdk-image/ j2sdk-trim

# Move trimmed jre to AeroFS main repo

    rm -rf ~/repos/aerofs/resource/client/osx/jre/
    mv j2sdk-trim/jre/ ~/repos/aerofs/resource/client/osx/jre


IMPORTANT FINAL STEPS
---------------------

    !!!
    !!! Revert `cacerts`, `local_policy.jar` and `US_export_policy.jar` to their
    !!! original state. See the Windows README for more information.
    !!!

    cd ~/repos/aerofs
    git checkout -f resource/client/osx/jre/lib/security/cacerts
    git checkout -f resource/client/osx/jre/lib/security/local_policy.jar
    git checkout -f resource/client/osx/jre/lib/security/US_export_policy.jar

