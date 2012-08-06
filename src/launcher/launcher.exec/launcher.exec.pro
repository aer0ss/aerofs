include(../common.pri)

# general settings
TEMPLATE = app
CONFIG += console
CONFIG -= app_bundle
DESTDIR = $$PWD/../bin/$$OS
TARGET = aerofs

macx {
    LIBS += -framework JavaVM
}
linux {
    LIBS += -ljava -lverify
}
linux-g++ {
    # 32-bit linux is the default
    LIBS += -L "$$(JAVA_HOME)/jre/lib/i386"
}
linux-g++-64 {
    # qmake should automatically use this scope on 64-bit machines
    LIBS += -L "$$(JAVA_HOME)/jre/lib/amd64"
}
win32 {
    LIBS += -ladvapi32
}

# Link with our liblauncher
LIBS += -L$$PWD/../lib -llauncher
DEPENDPATH += $$PWD/../launcher.lib
unix:  PRE_TARGETDEPS += $$PWD/../lib/liblauncher.a
win32: PRE_TARGETDEPS += $$PWD/../lib/launcher.lib

# source files
SOURCES += \
    launcher.cpp
