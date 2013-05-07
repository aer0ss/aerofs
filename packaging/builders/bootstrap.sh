#!/bin/bash -e
rm -rf bootstrap

RESOURCES=../src/bootstrap/resources
OPT=bootstrap/opt/bootstrap
BIN=bootstrap/usr/bin
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

# Init script.
mkdir -p $INIT
cp $RESOURCES/bootstrap $INIT

# Put the script in user bin for convenience as well.
mkdir -p $BIN
cd $BIN
ln -s ../../etc/init.d/bootstrap .
