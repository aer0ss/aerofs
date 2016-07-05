The AeroFS progress-monitor is the native code that presents a progress bar
when the golang updater is downloading additional content.

# Generating Binaries

This step must be done separately on each platform (OSX, Linux32, Linux64,
Windows). OSX and Linux binaries can be built on OSX. Windows support is not yet implemented.

## OSX

Run `make osx` to build.

## Linux32

Run `make linux32` to build.

## Linux64

Run `make linux64` to build.

## Windows

In the `aerofs-windows` vagrant machine (see
`~/repos/aerofs/tools/build/windows`), open powershell (as administrator) and
run (use `alt+space+e+p` to paste after enabling the "Bidirectional Shared
Clipboard" in VirtualBox settings for this machine):

    cd "C:\Program Files\Microsoft Visual Studio 12.0\VC"
    .\vcvarsall x86

    $env:Include = "C:\Program Files\Microsoft Visual Studio 12.0\VC\include;C:\Program Files\Microsoft SDKs\Windows\v7.1\Include"
    $env:Lib = "C:\Program Files\Microsoft Visual Studio 12.0\VC\lib;C:\Program Files\Microsoft SDKs\Windows\v7.1\Lib"
    $env:Path = $env:Path + ";C:\Program Files\Microsoft Visual Studio 12.0\VC\bin;C:\Program Files\Microsoft SDKs\Windows\v7.1A\Bin"

    cd \\VBOXSVR\repos\aerofs\src\progress-monitor\windows
    msbuild progress-monitor.sln /p:Configuration=Release /t:Clean;Build
