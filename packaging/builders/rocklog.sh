#!/bin/bash -ue

rm -rf rocklog

DEBIAN=rocklog/DEBIAN
OPT=rocklog/opt/rocklog
RESOURCES=../src/rocklog/resources

mkdir -p $DEBIAN
for f in control
do
    cp $RESOURCES/$f $DEBIAN
done

mkdir -p $OPT
cp -r ../src/rocklog/ $OPT

# Remove unnecessary files
rm -rf $OPT/resources
