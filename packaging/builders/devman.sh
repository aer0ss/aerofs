#!/bin/bash -e
rm -rf devman

RESOURCES=../src/devman/resources
OPT=devman/opt/devman
INIT=devman/etc/init
DEBIAN=devman/DEBIAN

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control conffiles preinst postinst prerm postrm
do
    cp -r $RESOURCES/$f $DEBIAN
done

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/devman/*.jar $OPT

# Upstart-related file copies.
mkdir -p $INIT
cp $RESOURCES/devman.conf $INIT
cp $RESOURCES/devman $OPT
cp $RESOURCES/devman.yml $OPT
cp $RESOURCES/logback.xml $OPT
