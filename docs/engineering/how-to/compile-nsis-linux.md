Compiling makensis on Linux with logging support
===

NSIS provides the "LogSet on" instruction to automatically log all events to an install.log file. Unfortunately, in order to use this feature, makensis must be manually compiled with a special flag.

Note: NSIS releases are versionned in the form x.xx. As of this writing, we're using the 2.46 release. In this document, we'll write 'x.xx' to refer to whatever release you're using.

Set the stage
---

On b.arrowfs.org:

    cd /usr/local
    $ mkdir nsis
    $ cd nsis

Get the stuff
---

wget "nsis-x.xx.zip", "nsis-x-xx-src.tar.gz", and "nsis-x.xx-log.zip" from sourceforge (http://sourceforge.net/projects/nsis/files/NSIS%202/).

    tar -jxvf nsis-x.xx-src.tar.bz2
    $ unzip nsis-x.xx.zip

(we'll deal with nsis-x.xx-log.zip later)

Build makensis
---

    cd /usr/local/nsis/nsis-2.xx-src
    $ scons NSIS_CONFIG_LOG=yes SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA=no PREFIX=/usr/local/nsis/nsis-2.xx install-compiler

This will build makensis and place it in the /usr/local/nsis/nsis-2.xx/bin directory

Do some symlink magic
---

    cd /usr/local/nsis/nsis-2.xx
    mkdir share
    cd share
    ln -s /usr/local/nsis/nsis-2.xx nsis

Replace the default stubs
---

Stubs are pre-compiled Windows executables that makensis will use to create our installer. We need to replace the default stubs with stubs that support logging.

    cd /usr/local/nsis/nsis-x.xx
    mv Stubs Stubs-no-log
    cd ..
    unzip nsis-x.xx-log.zip
    rm makensis.exe         # useless clutter
    mv Stubs nsis-x.xx/
