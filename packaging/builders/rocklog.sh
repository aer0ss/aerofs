#!/bin/bash -ue

rm -rf rocklog

DEBIAN=rocklog/DEBIAN
OPT=rocklog/opt/rocklog
RESOURCES=../src/rocklog/resources

mkdir -p $DEBIAN
for f in control
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp $RESOURCES/$f $DEBIAN
done

mkdir -p $OPT
cp -a ../src/rocklog/ $OPT

# Remove unnecessary files
rm -rf $OPT/resources
