#!/bin/bash -ue

ENTERPRISE=../tools/enterprise
OPT=installers/opt/installers

rm -rf $OPT
mkdir -p $OPT/tools

# TODO (MP) need to figure out a better way to manage debians when dealing
# with different repositories.
WEB_ROOT=https://github.arrowfs.org/drewf/damage/raw/master
wget -q --output-document=$ENTERPRISE/installers/osx/damage.py $WEB_ROOT/damage.py
wget -q --output-document=$ENTERPRISE/installers/osx/damage.py $WEB_ROOT/add_file_to_image.sh

for os in win osx linux
do
    cp -r $ENTERPRISE/installers/$os $OPT/tools
done

cp $ENTERPRISE/installers/pull.sh $OPT/tools
