# This is a QtCreator project file
#
# To open this file, download Qt from: http://qt.nokia.com/downloads/
#
# Note: we do not use the Qt libraries, only Qt creator as our IDE.
# If you want, you can also use qmake to generate a Makefile from this file

TEMPLATE = lib          # Building a shared library
CONFIG -= qt            # Do not use Qt
TARGET = ScryptPy       # The name of our library. Qmake will add the "lib" prefix on *nix platforms
DESTDIR = $$PWD/build   # Put the resulting library in the "build" folder within the python directory


INCLUDEPATH += \
    $$PWD/../3rd_party/include \
    $$PWD/../src/scrypt # scrypt directory is here so that ScryptPy.c can find crypto_scrypt.h

# Find whether we are building on a 32 or 64 bits machine
contains(QMAKE_HOST.arch, i686):{
    ARCH="32"
} else:contains(QMAKE_HOST.arch, x86_64):{
    ARCH="64"
} else {
    ARCH=""
}

win32 {
    TARGET = lib$$TARGET  # Pyhton wants the "lib" prefix even on Windows
    INCLUDEPATH += "C:/Python27/include"
    LIBS += -LC:/Python27/libs -L"$$PWD/../3rd_party/lib/win32/"
    LIBS += -llibeay32
}

macx {
    # Those flags set what version of the OS X SDK we want to build against
    SDKROOT = /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.6.sdk/

    # Linker settings
    LIBS += -L"$$PWD/../3rd_party/lib/osx/"
    LIBS += -lcrypto.1.0.0 -framework Python

    # Misc OSX settings
    INCLUDEPATH += "$$(SDKROOT)/System/Library/Frameworks/Python.framework/Headers"
}

linux-g++ {
    LIBS += -L"$$PWD/../3rd_party/lib/linux$$ARCH/"
    LIBS += -lcrypto

    INCLUDEPATH += /usr/include/python2.7
}

SOURCES += \
    ../src/scrypt/crypto_aesctr.c \
    ../src/scrypt/crypto_scrypt-nosse.c \
    ../src/scrypt/sha256.c \
    ScryptPy.c

HEADERS += \
    ../src/scrypt/crypto_aesctr.h \
    ../src/scrypt/crypto_scrypt.h \
    ../src/scrypt/sha256.h \
    ../src/scrypt/sysendian.h

# Install information (put binaries into python-lib folder)
macx {
    PLATFORM = "osx"
    install.extra += ln -sf libScryptPy.dylib ../../python-lib/aerofs/lib/osx/libScryptPy.so;
    install.extra += cp ../3rd_party/lib/osx/libcrypto.1.0.0.dylib ../../python-lib/aerofs/lib/osx/;
    install.files = build/libScryptPy.dylib
}
linux-g++ {
    PLATFORM = "linux$$ARCH"
    install.extra += ln -sf libScryptPy.so.1.0.0 ../../python-lib/aerofs/lib/$$PLATFORM/libScryptPy.so;
    install.extra += cp ../3rd_party/lib/$$PLATFORM/libcrypto.so.1.0.0 ../../python-lib/aerofs/lib/$$PLATFORM/;
    install.files = build/libScryptPy.so.1.0.0
}
install.path = ../../python-lib/aerofs/lib/$$PLATFORM
INSTALLS += install
