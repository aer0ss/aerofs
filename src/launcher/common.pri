#unix is defined for OS X too, so we define linux as (unix and not osx)
unix:!macx:CONFIG += linux

AEROFS_ROOT = $$PWD/../../

macx {
    OS = "osx"
    DEFINES += MACOSX
    RESOURCE_CLIENT = $$AEROFS_ROOT/resource/client/osx
}
linux {
    DEFINES += LINUX
}
linux-g++ {
    OS = "linux32"
    RESOURCE_CLIENT = $$AEROFS_ROOT/resource/client/linux/i386
}
linux-g++-64 {
    OS = "linux64"
    RESOURCE_CLIENT = $$AEROFS_ROOT/resource/client/linux/amd64
}
win32 {
    OS = "win"
    DEFINES += WIN32
    RESOURCE_CLIENT = $$AEROFS_ROOT/resource/client/win

    # Link statically with MSVC C++ runtime library to avoid depending on MSVC100.DLL and MSVCR100.DLL
    QMAKE_CXXFLAGS_RELEASE -= -MD
    QMAKE_CXXFLAGS_RELEASE += -MT
    QMAKE_CXXFLAGS_DEBUG -= -MD
    QMAKE_CXXFLAGS_DEBUG += -MT

}

CONFIG -= qt

macx {
    INCLUDEPATH += "$$PWD/openjdk/include" \
                   "$$PWD/openjdk/include/darwin"
}
linux {
    INCLUDEPATH += "$$(JAVA_HOME)/include" \
                   "$$(JAVA_HOME)/include/linux"
}
win32 {
    # Note: from now on we should be using the openjdk headers that we ship in $$PWD/openjdk/include
    # I don't have the time to do it and test it right now, but the next time someone builds this
    # on Windows, please have a look at it.
    INCLUDEPATH += "$$(JAVA_HOME)/include" \
                   "$$(JAVA_HOME)/include/win32"
}
