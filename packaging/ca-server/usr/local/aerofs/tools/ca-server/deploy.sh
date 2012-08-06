#!/bin/bash -e

if [ $# -ne 1 ]
then
    echo "usage: $0 <deploy_path>"
    exit 1
fi

thisdir=$(dirname $0)
deploy_path=$1

mkdir -p $deploy_path
cp $thisdir/Makefile $deploy_path
cp $thisdir/openssl.cnf $deploy_path

cd $deploy_path
sed -i.bak "s#/dev/null#$(pwd)#g" openssl.cnf
rm *.bak
make init
