#!/bin/bash
set -e -u

OUTPUT_DIR=build/repackaging
OPT=$OUTPUT_DIR/opt/repackaging
TOOLS=$OPT/tools
ENTERPRISE=../tools/enterprise

mkdir -p $TOOLS

# Fetch python dependency packages to cache
REQUIREMENTS=$ENTERPRISE/repackaging/requirements.txt
SDIST_CACHE=$HOME/.aerofs-cache/pip/repackaging
tools/pip-prefetch.sh $REQUIREMENTS $SDIST_CACHE

# Add dependency packages to package
SDIST=$OPT/sdist
mkdir -p $SDIST
cp -a $SDIST_CACHE/* $SDIST/
cp $REQUIREMENTS $SDIST/

# Include repackaging tools
for os in linux osx win
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp -a $ENTERPRISE/repackaging/$os $TOOLS
done

cp $ENTERPRISE/repackaging/pull-installers.sh $TOOLS
cp $ENTERPRISE/repackaging/repackage-installers.sh $TOOLS
