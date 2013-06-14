#!/bin/bash -ue
rm -rf zephyr

RESOURCES=../src/zephyr/resources
OPT=zephyr/opt/zephyr
INIT=zephyr/etc/init
DEBIAN=zephyr/DEBIAN

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control conffiles preinst prerm postrm
do
    cp -r $RESOURCES/$f $DEBIAN
done

# Java-related file copies.
mkdir -p $OPT
cp ../out.ant/artifacts/zephyr/*.jar $OPT

# Upstart-related file copies.
mkdir -p $INIT
cp $RESOURCES/zephyr.conf $INIT
cp $RESOURCES/logback.xml $OPT
cp $RESOURCES/zephyr $OPT
cp $RESOURCES/banner.txt $OPT
