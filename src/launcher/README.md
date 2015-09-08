AeroFS Launcher is the native code entrypoints for the gui and daemon

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

    cd \\VBOXSVR\repos\aerofs\src\launcher
    rm -r build
    mkdir build
    cd build
    qmake CONFIG+=release ..\launcher.pro
    nmake

If the executables don't change in git (ie. don't get recompiled), but you
think they should, you should delete `resource/client/win/aerofs{,d}.exe`. Why?
Something about the VM being bad at tracking dates properly for `nmake`.
