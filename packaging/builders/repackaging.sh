#!/bin/bash -ue

ENTERPRISE=../tools/enterprise
OPT=repackaging/opt/repackaging

rm -rf $OPT
mkdir -p $OPT/tools

for os in win osx linux
do
    cp -r $ENTERPRISE/repackaging/$os $OPT/tools
done

cp $ENTERPRISE/repackaging/pull-installers.sh $OPT/tools
cp $ENTERPRISE/repackaging/repackage-installers.sh $OPT/tools
