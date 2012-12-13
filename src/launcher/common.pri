#unix is defined for OS X too, so we define linux as (unix and not osx)
unix:!macx:CONFIG += linux

macx {
    OS = "osx"
    DEFINES += MACOSX
}
linux {
    DEFINES += LINUX
}
linux-g++ {
    OS = "linux32"
}
linux-g++-64 {
    OS = "linux64"
}
win32 {
    OS = "win"
    DEFINES += WIN32
}

CONFIG -= qt

macx {
    INCLUDEPATH += /System/Library/Frameworks/JavaVM.framework/Versions/A/Headers/
}
linux {
    INCLUDEPATH += "$$(JAVA_HOME)/include" \
                   "$$(JAVA_HOME)/include/linux"
}
win32 {
    INCLUDEPATH += "$$(JAVA_HOME)/include" \
                   "$$(JAVA_HOME)/include/win32"
}

# force a release build to avoid accidental deployment of a debug build
# (not that it matters, just better to avoid it)
CONFIG -= debug
CONFIG += release
