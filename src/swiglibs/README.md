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

In some cases, the default mkspec picked by the Qt install will not work,
in which case you may need to explictly set it:

    qmake -spec unsupported/macx-clang ../aerofsd.pro && make


Linux
-----

    export JAVA_HOME=$(readlink -f $(which java) | sed 's/\/jre\/bin\/java//')


Windows
-------

Use `nmake`, which is part of Visual C++ command line tools, instead of `make`.


