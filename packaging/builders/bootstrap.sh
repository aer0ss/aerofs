#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

SERVICE=bootstrap
RESOURCES=../src/$SERVICE/resources
OUTPUT_DIR=build/$SERVICE
OPT=$OUTPUT_DIR/opt/$SERVICE
DEBIAN=$OUTPUT_DIR/DEBIAN

mkdir -p $OPT
mkdir -p $DEBIAN
mkdir -p $OUTPUT_DIR/etc/init
mkdir -p $OUTPUT_DIR/usr/bin
mkdir -p $OUTPUT_DIR/var/log/$SERVICE

# XXX unfortunately much of this is shared with generate_service_deb_template.sh.
# Need to keep these separate, because bootstrap needs to run as root.
cp $RESOURCES/debian/control $DEBIAN/control
for f in preinst prerm postrm
do
    cp $RESOURCES/debian/$f $DEBIAN/$f
    chmod 755 $DEBIAN/$f
done

cp $RESOURCES/bootstrap.conf $OUTPUT_DIR/etc/init/

# Java-related file copies.
cp ../out.ant/artifacts/$SERVICE/*.jar $OPT/
for jar in bootstrap-taskfile bootstrap-command
do
    cp ../out.ant/artifacts/$jar/$jar.jar $OPT/
done

# TODO (MP) logback.xml when we move to dropwizard.
cp $RESOURCES/logback.xml $OPT
cp $RESOURCES/logback-stdout.xml $OPT
cp $RESOURCES/bootstrap.yml $OPT

# Copy over additional scripts required in the bootstrap package.
cp -a $RESOURCES/scripts $OPT
for res in bootstrap-taskfile bootstrap-command install-cert
do
    # N.B. these scripts are somewhat user facing, hence the aerofs prefix.
    cp $RESOURCES/bin/$res $OUTPUT_DIR/usr/bin/aerofs-$res
    chmod a+x $OUTPUT_DIR/usr/bin/aerofs-$res
done

# Bootstrap task sets.
cp -a $RESOURCES/tasks $OPT/tasks
