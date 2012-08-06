TEMPLATE = subdirs
CONFIG += ordered
SUBDIRS = \
    launcher.lib \
    launcher.exec

launcher.exec.file = launcher.exec/launcher.exec.pro
launcher.exec.depends = launcher.lib
