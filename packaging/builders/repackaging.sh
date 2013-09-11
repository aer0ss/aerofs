#!/bin/bash
set -e -u

ENTERPRISE=../tools/enterprise
OPT=repackaging/opt/repackaging

rm -rf $OPT
mkdir -p $OPT/tools

# Fetch and include python dependency packages
REQUIREMENTS=$ENTERPRISE/repackaging/requirements.txt
SDIST=$OPT/sdist
SDIST_CACHE=pip-cache/repackaging
rm -rf $SDIST
tools/pip-prefetch.sh $REQUIREMENTS $SDIST_CACHE
mkdir -p $SDIST
cp -a $SDIST_CACHE/* $SDIST/
cp $REQUIREMENTS $SDIST/

for os in linux osx win
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp -a $ENTERPRISE/repackaging/$os $OPT/tools
done

cp $ENTERPRISE/repackaging/pull-installers.sh $OPT/tools
cp $ENTERPRISE/repackaging/repackage-installers.sh $OPT/tools
