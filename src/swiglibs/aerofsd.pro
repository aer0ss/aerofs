# This is a QtCreator project file
#
# To open this file, download Qt from: http://qt.nokia.com/downloads/
#
# Note: we do not use the Qt libraries, only Qt creator as our IDE.
# If you want, you can also use qmake to generate a Makefile from this file

TEMPLATE = lib          # Building a shared library
CONFIG -= qt            # Do not use Qt
TARGET = aerofsd        # The name of our library. Qmake will add the "lib" prefix on *nix platforms

INCLUDEPATH += $$PWD/3rd_party/include
QMAKE_CXX_FLAGS += -D_FILE_OFFSET_BITS=64 -fmessage-length=0 -fno-rtti

# Find whether we are building on a 32 or 64 bits machine
contains(QMAKE_HOST.arch, i686):{
    ARCH="i386"
} else:contains(QMAKE_HOST.arch, x86_64):{
    ARCH="amd64"
} else {
    ARCH=""
}

macx {
    # Those flags set what version of the OS X SDK we want to build against
    SDKROOT = /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.6.sdk/
    # TODO (WW) since we are using 10.6 SDK anyway (see above), should we set -mmacosx-version-min to 10.6?
    QMAKE_CXXFLAGS += -mmacosx-version-min=10.5 -arch x86_64

    # Linker settings
    LIBS += -L"$$PWD/3rd_party/lib/osx/"
    LIBS += -lssl.1.0.0 -lcrypto.1.0.0 -framework AppKit

    # Misc OSX settings
    INCLUDEPATH += "$$(SDKROOT)/System/Library/Frameworks/JavaVM.framework/Headers"
    #QMAKE_CXXFLAGS += -isysroot $(SDKROOT)
}
linux-g++ {
    INCLUDEPATH += "$$(JAVA_HOME)/include"
    INCLUDEPATH += "$$(JAVA_HOME)/include/linux"
    LIBS += -L"$$PWD/3rd_party/lib/linux/$$ARCH/"
    LIBS += -lssl.1.0.0 -lcrypto.1.0.0
    # Driver needs to be able to handle files larger than 2GB; see aerofs-585
    DEFINES += "_FILE_OFFSET_BITS=64"
}
win32 {
    INCLUDEPATH += "$$(JAVA_HOME)\\include"
    INCLUDEPATH += "$$(JAVA_HOME)\\include\\win32"
    QMAKE_CXXFLAGS += -DUNICODE -D_UNICODE

    # Linker settings
    LIBS += -L"$$PWD/3rd_party/lib/win32/"
    LIBS += -lssleay32 -llibeay32 -lpsapi -lws2_32 -luser32 -lgdi32 -ladvapi32 -lshlwapi -lshell32 -liphlpapi
}

SOURCES += \
    src/driver/AeroFS.cpp \
    src/driver/Main.cpp \
    src/driver/Driver.cpp \
    src/pkcs10/PKCS10.c \
    src/scrypt/crypto_aesctr.c \
    src/scrypt/crypto_scrypt-nosse.c \
    src/scrypt/sha256.c

unix {
    # Files that should be compiled on both OS X and Linux
    SOURCES += \
        src/driver/driver_nix.cpp
}

linux-g++ {
    # Files that should be compiled on linux only
    SOURCES += \
        src/driver/driver_linux.cpp
}

win32 {
    SOURCES += \
        src/driver/driver_win.cpp
}

macx {
    OBJECTIVE_SOURCES += \
        src/driver/driver_osx.mm
}

HEADERS += \
    src/logger.h \
    src/Util.h \
    src/driver/AeroFS.h \
    src/driver/Driver.h \
    src/pkcs10/PKCS10.h \
    src/scrypt/crypto_aesctr.h \
    src/scrypt/crypto_scrypt.h \
    src/scrypt/sha256.h \
    src/scrypt/sysendian.h

SWIG_FILES += \
    src/driver/driver.swg \
    src/pkcs10/pkcs10.swg \
    src/scrypt/scrypt.swg

OTHER_FILES += $$SWIG_FILES \
    src/scrypt/crypto_scrypt-nosse.patch \
    src/scrypt/crypto_scrypt.patch


# Preprocess *.swg files in the SWIG_FILES variable to generate the swig wrappers
# Documentation on those commands: http://www.qtcentre.org/wiki/index.php?title=Undocumented_qmake
gen_swg.input = SWIG_FILES
gen_swg.output  = ${QMAKE_FILE_PATH}/${QMAKE_FILE_BASE}_wrap.cpp
gen_swg.variable_out = SOURCES
gen_swg.commands = $(MKDIR) "\"$$PWD/gen/com/aerofs/swig/${QMAKE_FILE_BASE}\"" && swig -c++ -java \
                        -outdir "\"$$PWD/gen/com/aerofs/swig/${QMAKE_FILE_BASE}\"" \
                        -package "com.aerofs.swig.${QMAKE_FILE_BASE}"  \
                        -o  "${QMAKE_FILE_OUT}" \
                        "${QMAKE_FILE_NAME}"

QMAKE_EXTRA_COMPILERS += gen_swg

