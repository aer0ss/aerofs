#!/bin/bash -e

thisdir=$(dirname $0)
deploy_path=prod

mkdir -p $deploy_path
cp $thisdir/Makefile $deploy_path
cp $thisdir/openssl.cnf $deploy_path

cd $deploy_path
sed -i.bak "s#/dev/null#$(pwd)#g" openssl.cnf
rm *.bak
echo ">>> Depoloying to \"$deploy_path\""
make init
