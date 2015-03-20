We use OpenSSL.  However, it can be a pain to build.

Steps to using a new openssl:

----------------------------------
1) Build OpenSSL for each platform.

  -------------
  OSX build:

    Update the openssl_aerofs.rb brew script in this folder to use the latest
    url and shasum, then:

    # brew install ./openssl_aerofs.rb
    # cp $(brew --prefix)/Cellar/openssl_aerofs/1.0.1m/*.dylib ~/repos/aerofs/resource/client/osx/

    Why use a brew script?  We have to patch upstream openssl, since it
    specifies an absolute RPATH at build time (but we need a relative path).
    This seemed easiest.

    Alternatively, you can just build manually as described by the docs and use
    install_name_tool(1) to adjust the linker paths and library names, but
    that's more manual effort.

  -------------
  Linux build:

    You'll have to do this twice; once for 32-bit and once for 64-bit.

    1) Get the source:

    # cd $HOME
    # mkdir scratch
    # cd scratch
    # # Obviously, you should pick the latest link.
    # wget http://www.openssl.org/source/openssl-1.0.1m.tar.gz
    # tar xzvf openssl*.tar.gz
    # cd openssl-1.0.1m/

    2) Configure:

    # # For 32-bit Linux, make the last argument "linux-elf" instead of "linux-x86_64"
    # perl ./Configure --prefix=/usr/local shared linux-x86_64

    3) Build:

    # make
    # make test

    4) Copy libssl and libcrypto to repos/aerofs/resource/client/linux/(i386|amd64)/ .

  -------------
  Windows build:

    1) Install on a Windows machine:
       Visual Studio 2010
       Activestate Perl: <http://www.activestate.com/activeperl/downloads>
       Netwide Assembler (nasm): <http://www.nasm.us/pub/nasm/releasebuilds/>

    2) Download the OpenSSL source as above

    3) Open a Visual Studio Command Prompt (x86, not x64)

      > cd path\to\openssl-1.0.1m
      > "C:\Program Files (x86)\nasm\nasmpath.bat"
      > perl Configure VC-WIN32
      > ms\do_nasm.bat
      > nmake -f ms\nt.mak

    4) Copy the binaries from out32:
       * libeay32.lib and ssleay32.lib should be copied to src/swiglibs/3rd_party/lib/win32/

----------------------------------
2) copy the headers into the folder containing this README:

  # Wipe out the old headers:
    cd $HOME/repos/aerofs/src/swiglibs/3rd_party/include
    mv openssl openssl_old
  # Copy in the new headers
    cd $HOME
    rm -rf scratch
    mkdir scratch
    cd scratch
        # Obviously, you should pick the latest link.
    wget http://www.openssl.org/source/openssl-1.0.1m.tar.gz
    tar xzvf openssl*.tar.gz
    cd openssl-*
    cd include
    # Mind the slashes
    rsync -avL openssl $HOME/repos/aerofs/src/swiglibs/3rd_party/include/

----------------------------------
3) rebuild aerofsd.so for all platforms (beyond the scope of this README)

