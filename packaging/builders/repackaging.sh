#!/bin/bash -ue

ENTERPRISE=../tools/enterprise
OPT=repackaging/opt/repackaging

rm -rf $OPT
mkdir -p $OPT/tools

for os in android linux osx win
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp -a $ENTERPRISE/repackaging/$os $OPT/tools
done

cp $ENTERPRISE/repackaging/pull-installers.sh $OPT/tools
cp $ENTERPRISE/repackaging/repackage-installers.sh $OPT/tools
