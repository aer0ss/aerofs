include(../common.pri)

TARGET = launcher
TEMPLATE = lib
CONFIG += staticlib
DESTDIR = $$PWD/../lib

HEADERS += \
    liblauncher.h \
    util.h

SOURCES += \
    liblauncher.cpp \
    util.cpp

macx {
    SOURCES += \
        util_nix.cpp
}

linux {
    SOURCES += \
        util_nix.cpp
}

win32 {
    SOURCES += util_win.cpp
    QMAKE_CXXFLAGS += -DUNICODE -D_UNICODE

}
