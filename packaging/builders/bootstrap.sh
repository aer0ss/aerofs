#!/bin/bash -ue
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
cp $RESOURCES/bin/bootstrap $INIT/aerofs-bootstrap

# Configuration stuff. Empty defaults.
mkdir -p $OPT
touch $OPT/string.properties
touch $OPT/configuration.properties
touch $OPT/labeling.properties

# Put the script in user bin for convenience as well.
mkdir -p $BIN
for res in set-config-url install-cert-file install-cert-raw
do
    # N.B. these scripts are somewhat user facing, hence the aerofs prefix.
    cp $RESOURCES/bin/$res $BIN/aerofs-$res
    chmod a+x $BIN/aerofs-$res
done
cd $BIN
ln -s ../../etc/init.d/aerofs-bootstrap aerofs-bootstrap
