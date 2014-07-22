#!/bin/bash -e

usage()
{
    echo "Usage: $0 [<password>]"
    echo "If no password is specified, stdin is used to collect the password."
}

if [ $# -ne 0 ] && [ $# -ne 1 ]
then
    usage
    exit 1
fi

if [ "$@" = "-h" ]
then
    usage
    exit 0
fi

thisdir=$(dirname $0)
password="$@"
deploy_path=prod

mkdir -p $deploy_path

cp $thisdir/Makefile $thisdir/$deploy_path
cp $thisdir/openssl.cnf $thisdir/$deploy_path
cd $thisdir/$deploy_path

# This ensures a (usually) unique DN for our CA to work around a chrome and firefox
# bug. These browsers will fail weirdly when you try to create an exception for a new
# cert that has a DN that it already knows about.
random=$(openssl rand -hex 16 | tr '[:lower:]' '[:upper:]')
echo "commonName = AeroFS-$random" >> openssl.cnf

if [ ! -z "$password" ]
then
    echo "$password" > passwd
    chmod 0600 passwd
fi

sed -i.bak "s#/dev/null#$(pwd)#g" openssl.cnf
rm *.bak
echo ">>> Depoloying to \"$deploy_path\""

if [ -z "$password" ]
then
    make init-manual
else
    make init-auto
fi
