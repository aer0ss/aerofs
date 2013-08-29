#!/bin/bash -ue
rm -rf web

RESOURCES=../src/web/resources
OPT=web/opt/web
DEBIAN=web/DEBIAN
NGINX=web/etc/nginx
UWSGI=web/etc/uwsgi
EXTRA=web/opt/web/extra

# Debian-related file copies.
mkdir -p $DEBIAN
for f in control postinst prerm conffiles
do
    cp $RESOURCES/$f $DEBIAN
done

# Web repo and python-lib copy
mkdir -p $OPT
mkdir -p $EXTRA
# N.B. (PH) this will not copy dotfiles
cp -r ../src/web/* $OPT
rm $OPT/development.ini
cp -r ../src/python-lib $EXTRA

# remove unnecessary files
rm -r $OPT/resources
