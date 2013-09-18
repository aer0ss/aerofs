#!/bin/bash -ue
rm -rf web

RESOURCES=../src/web/resources
OPT=web/opt/web
DEBIAN=web/DEBIAN
NGINX=web/etc/nginx
UWSGI=web/etc/uwsgi
EXTRA=web/opt/web/extra

# PIP_CACHE is a flat directory of .tar.gz source files from PyPI.
PIP_CACHE=$HOME/.aerofs-cache/pip/web

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

# Fetch pip source packages, if needed
tools/pip-prefetch.sh "../src/web/requirements.txt" "$PIP_CACHE"
# Copy source packages to /opt/web/sdist, so they'll be available at package
# install time.
mkdir -p $OPT/sdist
cp $PIP_CACHE/* $OPT/sdist/

# remove unnecessary files
rm -r $OPT/resources
