Building OpenJDK 7 for OS X
===========================


Pre-requisites
--------------

- Mac OS X 10.7 and Xcode 4.5.2 - Does't work on Mavericks
- Apple's Java 6 to serve as a bootstrap JDK


Building
--------

Instructions are at https://wiki.openjdk.java.net/display/MacOSXPort/Main

# Get the code

    hg clone --pull http://hg.openjdk.java.net/jdk7u/jdk7u openjdk7u
    cd openjdk7u
    bash get_source.sh

# Checkout a known release tag

    bash ./make/scripts/hgforest.sh update -c jdk7u60-b04

# Build

    unset JAVA_HOME
    CPATH="/usr/X11/include" LANG=C make ALLOW_DOWNLOADS=true SA_APPLE_BOOT_JAVA=true ALWAYS_PASS_TEST_GAMMA=true ALT_BOOTDIR=`/usr/libexec/java_home -v 1.6` HOTSPOT_BUILD_JOBS=`sysctl -n hw.ncpu`


Packaging
---------

# Use our tool to remove unnecessary files

    git clone https://github.com/aerofs/openjdk-trim.git
    cd openjdk-trim
    ./jdk-trim osx ../openjdk7u/build/j2sdk-image/ j2sdk-trim

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

