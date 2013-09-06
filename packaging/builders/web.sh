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
# cp -r is BAD, prefer cp -a or cp -R for OSX compatibility; man 1 cp
cp -a ../src/web/* $OPT
rm -rf $OPT/development
cp -a ../src/python-lib $EXTRA

# remove unnecessary files
rm -r $OPT/resources
