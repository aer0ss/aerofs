#!/bin/bash -e
rm -rf bootstrap

RESOURCES=../src/bootstrap/resources
OPT=bootstrap/opt/bootstrap
INIT=bootstrap/etc/init.d
DEBIAN=bootstrap/DEBIAN

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control conffiles preinst prerm postrm postinst
do
    cp -r $RESOURCES/$f $DEBIAN
done

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/bootstrap/*.jar $OPT
cp $RESOURCES/logback.xml $OPT
cp $RESOURCES/bootstrap.tasks $OPT
cp -r $RESOURCES/scripts $OPT

mkdir -p $INIT
cp $RESOURCES/bootstrap $INIT
