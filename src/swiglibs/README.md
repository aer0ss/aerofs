AeroFS Driver is a bunch of native code used by the desktop app

# Generate jar

Create a file named gradle.properties from the following skeleton. You'll need
to ask someone for the username and password.

    nexusRepo=http://repos.arrowfs.org
    nexusUser=
    nexusPass=

Then, run `gradle build` to build the jar, or `gradle uploadArchives` to build
a new jar and push it to our registry.

NB.  *always* bump the version number in build.gradle before uploading jars.

# Generate native lib

This step must be done seperately on each platform (OSX, Linux32, Linux64,
Windows).

## OSX, Linux32, and Linux64

Simply run `make osx`, `make linux32`, or `make linux64`, respectively.

## Windows

Unfortunately, the process for building on Windows is slightly more involved.

In the `aerofs-windows` vagrant machine (see
`~/repos/aerofs/tools/build/windows`), open powershell (as administrator) and
run (use `alt+space+e+p` to paste after enabling the "Bidirectional Shared
Clipboard" in VirtualBox settings for this machine):

    cd "C:\Program Files\Microsoft Visual Studio 12.0\VC"
    .\vcvarsall x86

    $env:Include = "C:\Program Files\Microsoft Visual Studio 12.0\VC\include;C:\Program Files\Microsoft SDKs\Windows\v7.1\Include"
    $env:Lib = "C:\Program Files\Microsoft Visual Studio 12.0\VC\lib;C:\Program Files\Microsoft SDKs\Windows\v7.1\Lib"
    $env:Path = $env:Path + ";C:\Qt\5.5\msvc2013\bin;C:\Program Files\Microsoft Visual Studio 12.0\VC\bin;C:\Program Files\Microsoft SDKs\Windows\v7.1A\Bin"

    cd \\VBOXSVR\repos\aerofs\src\swiglibs
    rm -r build
    mkdir build
    cd build
    qmake ..\aerofsd.pro
    nmake

    cp release\aerofsd.dll \\VBOXSVR\repos\aerofs\resource\client\win\aerofsd.dll
