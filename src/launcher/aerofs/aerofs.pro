include(../common.pri)

# general settings
TEMPLATE = app
CONFIG -= app_bundle
DESTDIR = $$RESOURCE_CLIENT
TARGET = aerofs

macx {
    # Tells the linker where to find libjvm.dylib at run time
    QMAKE_LFLAGS += "-Wl,-rpath,@executable_path/jre/lib/server"
}

linux {
    LIBS += -ljvm -ljava -lverify
}
linux-g++ {
    # 32-bit linux is the default
    LIBS += -L"$$(JAVA_HOME)/jre/lib/i386" -L"$$(JAVA_HOME)/jre/lib/i386/server"
}
linux-g++-64 {
    # qmake should automatically use this scope on 64-bit machines
    LIBS += -L"$$(JAVA_HOME)/jre/lib/amd64" -L"$$(JAVA_HOME)/jre/lib/amd64/server"
}
win32 {
    DESTDIR = $$RESOURCE_CLIENT
    DEFINES += _UNICODE UNICODE
    LIBS += -ladvapi32 -luser32
}

# Icon settings
RC_FILE = aerofs.rc

# Link with our liblauncher
LIBS += -L$$PWD/../lib -llauncher
DEPENDPATH += $$PWD/../launcher.lib
unix:  PRE_TARGETDEPS += $$PWD/../lib/liblauncher.a
win32: PRE_TARGETDEPS += $$PWD/../lib/launcher.lib

# header files
HEADERS += \
    $$PWD/../common/util.h

# source files
SOURCES += \
    main.cpp \
    $$PWD/../common/util.cpp

OTHER_FILES += \
    aerofs.rc
