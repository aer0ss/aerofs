#!/bin/bash
set -e -u

OUTPUT_DIR=build/rocklog
DEBIAN=$OUTPUT_DIR/DEBIAN
OPT=$OUTPUT_DIR/opt/rocklog
RESOURCES=../src/rocklog/resources

mkdir -p $DEBIAN
for f in control
do
    # cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
    cp $RESOURCES/$f $DEBIAN
done

mkdir -p $OPT
cp -a ../src/rocklog/{README.txt,retrace_client.py,rocklog.cfg,rocklog.py} $OPT/
