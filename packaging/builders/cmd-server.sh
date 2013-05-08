#!/bin/bash -ue
rm -rf cmd-server

RESOURCES=../src/command/resources
OPT=cmd-server/opt/cmd-server
INIT=cmd-server/etc/init
DEBIAN=cmd-server/DEBIAN

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control conffiles preinst postinst prerm postrm
do
    cp -r $RESOURCES/$f $DEBIAN
done

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/cmd-server/*.jar $OPT

# Upstart-related file copies.
mkdir -p $INIT
cp $RESOURCES/cmd-server.conf $INIT
cp $RESOURCES/cmd-server $OPT
cp $RESOURCES/cmd-server.yml $OPT
cp $RESOURCES/logback.xml $OPT
