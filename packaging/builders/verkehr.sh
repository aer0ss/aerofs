#!/bin/bash -ue
rm -rf verkehr

RESOURCES=../src/verkehr/resources
OPT=verkehr/opt/verkehr
INIT=verkehr/etc/init
DEBIAN=verkehr/DEBIAN

# deb packaging files
mkdir -p $DEBIAN
for f in control conffiles preinst prerm postrm
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp -a $RESOURCES/$f $DEBIAN
done

# all the jars verkehr needs
mkdir -p $OPT
cp ../out.ant/artifacts/verkehr/*.jar $OPT

# upstart config
mkdir -p $INIT
cp $RESOURCES/verkehr.conf $INIT

# verkehr config file
cp $RESOURCES/verkehr.yml $OPT

# verkehr command-line clients (uses same java code as AeroFS client)
cp $RESOURCES/subscriber $OPT
cp $RESOURCES/publisher $OPT
