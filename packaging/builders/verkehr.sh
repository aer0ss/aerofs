#!/bin/bash -ue
rm -rf verkehr

RESOURCES=../src/verkehr/resources
OPT=verkehr/opt/verkehr
INIT=verkehr/etc/init
DEBIAN=verkehr/DEBIAN

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control conffiles preinst prerm postrm
do
    cp -r $RESOURCES/$f $DEBIAN
done

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/verkehr/*.jar $OPT

# Upstart-related file copies.
mkdir -p $INIT
cp $RESOURCES/verkehr.conf $INIT
cp $RESOURCES/verkehr.yml $OPT
