#!/bin/bash -ue
rm -rf bootstrap

RESOURCES=../src/bootstrap/resources
OPT=bootstrap/opt/bootstrap
BIN=bootstrap/usr/bin
INIT=bootstrap/etc/init.d
DEBIAN=bootstrap/DEBIAN

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control conffiles preinst prerm postinst
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp -a $RESOURCES/$f $DEBIAN
done

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/bootstrap/*.jar $OPT
cp $RESOURCES/logback.xml $OPT
cp -a $RESOURCES/scripts $OPT

# Init script.
mkdir -p $INIT
cp $RESOURCES/bin/bootstrap $INIT/aerofs-bootstrap

# Configuration stuff. Empty defaults.
mkdir -p $OPT
touch $OPT/string.properties
touch $OPT/configuration.properties
touch $OPT/labeling.properties

# Put the script in user bin for convenience as well.
mkdir -p $BIN
for res in set-config-url install-cert
do
    # N.B. these scripts are somewhat user facing, hence the aerofs prefix.
    cp $RESOURCES/bin/$res $BIN/aerofs-$res
    chmod a+x $BIN/aerofs-$res
done
cd $BIN
ln -s ../../etc/init.d/aerofs-bootstrap aerofs-bootstrap
