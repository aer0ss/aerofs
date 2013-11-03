#!/bin/bash
set -eu
OUTPUT_DIR=build/bootstrap

RESOURCES=../src/bootstrap/resources
OPT=$OUTPUT_DIR/opt/bootstrap
BIN=$OUTPUT_DIR/usr/bin
DEBIAN=$OUTPUT_DIR/DEBIAN
INIT=$OUTPUT_DIR/etc/init

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp -a $RESOURCES/$f $DEBIAN
done

mkdir -p $OUTPUT_DIR/var/log/bootstrap

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/bootstrap-taskfile/*.jar $OPT
cp ../out.ant/artifacts/bootstrap-command/*.jar $OPT
cp ../out.ant/artifacts/bootstrap-service/*.jar $OPT
cp $RESOURCES/logback.xml $OPT
cp $RESOURCES/logback-service.xml $OPT
cp -a $RESOURCES/scripts $OPT

# Put the scripts in user bin for convenience as well.
mkdir -p $BIN
for res in bootstrap-taskfile bootstrap-command install-cert
do
    # N.B. these scripts are somewhat user facing, hence the aerofs prefix.
    cp $RESOURCES/bin/$res $BIN/aerofs-$res
    chmod a+x $BIN/aerofs-$res
done

# Bootstrap task sets.
cp -a $RESOURCES/tasks $OPT/tasks

# Create the bootstrap service conf file.
mkdir -p $INIT
cp $RESOURCES/bootstrap.conf $INIT
