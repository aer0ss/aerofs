#!/bin/bash -ue
rm -rf bootstrap

RESOURCES=../src/bootstrap/resources
OPT=bootstrap/opt/bootstrap
BIN=bootstrap/usr/bin
DEBIAN=bootstrap/DEBIAN
INIT=bootstrap/etc/init

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control conffiles
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp -a $RESOURCES/$f $DEBIAN
done

mkdir -p bootstrap/var/log/bootstrap

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/bootstrap-taskfile/*.jar $OPT
cp ../out.ant/artifacts/bootstrap-command/*.jar $OPT
cp ../out.ant/artifacts/bootstrap-service/*.jar $OPT
cp $RESOURCES/logback.xml $OPT
cp $RESOURCES/logback-service.xml $OPT
cp -a $RESOURCES/scripts $OPT

# Configuration stuff. Empty defaults.
mkdir -p $OPT
touch $OPT/string.properties
touch $OPT/configuration.properties
touch $OPT/labeling.properties

# Put the script in user bin for convenience as well.
mkdir -p $BIN
for res in bootstrap-taskfile bootstrap-command install-cert
do
    # N.B. these scripts are somewhat user facing, hence the aerofs prefix.
    cp $RESOURCES/bin/$res $BIN/aerofs-$res
    chmod a+x $BIN/aerofs-$res
done

# Create the bootstrap service conf file.
mkdir -p $INIT
cp $RESOURCES/bootstrap.conf $INIT
