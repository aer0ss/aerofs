#!/bin/bash -ue
rm -rf devman

RESOURCES=../src/devman/resources
OPT=devman/opt/devman
INIT=devman/etc/init
DEBIAN=devman/DEBIAN

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control conffiles preinst prerm postrm
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp -a $RESOURCES/$f $DEBIAN
done

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/devman/*.jar $OPT

# Upstart-related file copies.
mkdir -p $INIT
cp $RESOURCES/devman.conf $INIT
cp $RESOURCES/devman.yml $OPT
