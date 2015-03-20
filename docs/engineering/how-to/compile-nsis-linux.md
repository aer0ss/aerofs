Compiling makensis on Linux with logging support
===

NSIS provides the "LogSet on" instruction to automatically log all events to an install.log file. Unfortunately, in order to use this feature, makensis must be manually compiled with a special flag.

Note: NSIS releases are versionned in the form x.xx. As of this writing, we're using the 2.46 release, published in 2010.
Check [on the Sourceforge page](http://sourceforge.net/projects/nsis/files/NSIS%202/) for newer version.

Set the stage
---

On b.arrowfs.org:

    cd /usr/local
    mkdir nsis
    cd nsis

Get the stuff
---

wget "nsis-x.xx.zip", "nsis-x-xx-src.tar.gz", and "nsis-x.xx-log.zip" from sourceforge ([http://sourceforge.net/projects/nsis/files/NSIS%202/](http://sourceforge.net/projects/nsis/files/NSIS%202/)).

    wget http://sourceforge.net/projects/nsis/files/NSIS%202/2.46/nsis-2.46.zip/download -O nsis-2.46.zip
    wget http://sourceforge.net/projects/nsis/files/NSIS%202/2.46/nsis-2.46-log.zip/download -O nsis-2.46-log.zip
    wget http://sourceforge.net/projects/nsis/files/NSIS%202/2.46/nsis-2.46-src.tar.bz2/download -O nsis-2.46-src.tar.bz2
    tar -jxvf nsis-2.46-src.tar.bz2
    unzip nsis-2.46.zip

(we'll deal with nsis-x.xx-log.zip later)

Build makensis
---

You may need to apply this patch for recent versions of gcc (Ubuntu 14.10 for instance).

    cd /usr/local/nsis/nsis-2.46-src
    wget https://raw.githubusercontent.com/tpokorra/lbs-nsis/master/nsis/gcc46NameLookupChanges.patch
    patch SCons/Config/gnu gcc46NameLookupChanges.patch
    scons NSIS_CONFIG_LOG=yes SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA=no PREFIX=/usr/local/nsis/nsis-2.46 install-compiler

This will build makensis and place it in the /usr/local/nsis/nsis-2.xx/bin directory

Do some symlink magic
---

    cd /usr/local/nsis/nsis-2.46
    mkdir share
    cd share
    ln -s /usr/local/nsis/nsis-2.46 nsis

Replace the default stubs
---

Stubs are pre-compiled Windows executables that makensis will use to create our installer. We need to replace the default stubs with stubs that support logging.

    cd /usr/local/nsis/nsis-2.46
    mv Stubs Stubs-no-log
    cd ..
    unzip nsis-2.46-log.zip
    rm makensis.exe         # useless clutter
    mv Stubs nsis-2.46/
