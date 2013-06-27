#!/bin/bash -ue

ENTERPRISE=../tools/enterprise
OPT=installers/opt/installers

rm -rf $OPT
mkdir -p $OPT/tools

for os in win osx linux
do
    cp -r $ENTERPRISE/installers/$os $OPT/tools
done

cp $ENTERPRISE/installers/pull-binaries.sh $OPT/tools
cp $ENTERPRISE/installers/repackage-installers.sh $OPT/tools
