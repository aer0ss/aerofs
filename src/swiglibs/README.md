AeroFS Driver is a bunch of native code used by the desktop app

Generate jar
-------------

    gradle build


Upload jar to nexus
-------------------

Create file named `gradle.properties` from the following skeleton,
filling the username and password as needed:

    nexusRepo=http://repos.arrowfs.org
    nexusUser=
    nexusPass=


Then invoke the following gradle task:

    gradle uploadArchives


NB: *always* bump the version number in build.gradle before uploading jars


Generate native lib
-------------------

    rm -rf build && mkdir build && cd build && qmake ../aerofsd.pro && make


NB: gradle automatically calls qmake but this step must be done
separately on each platform for which native code is being built


OSX
---

The default mkspec may default to 10.7 SDK (as it is the first version to come
with libc++) but we still support 10.5 at this time so forcing it might be
necessary:

    QMAKESPEC=macx-clang 


Linux
-----

    export JAVA_HOME=$(readlink -f $(which java) | sed 's/\/jre\/bin\/java//')


Windows
-------

Use `nmake`, which is part of Visual C++ command line tools, instead of `make`.


