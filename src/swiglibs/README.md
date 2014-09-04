AeroFS Driver is a bunch of native code used by the desktop app

Generate jar
-------------

ant build


Generate native lib
-------------------

ant build_libraries


OSX
---

NB: on OSX the default mkspec may default to 10.7 SDK but
we still support 10.5 at this time so forcing it might be
necessary

QMAKESPEC=macx-g++ ant build_libraries


Linux
-----

export JAVA_HOME=$(readlink -f $(which java) | sed 's/\/jre\/bin\/java//')


Windows
-------

Build without ant:

mkdir -p build && cd build && qmake ../aerofsd.pro

nmake
