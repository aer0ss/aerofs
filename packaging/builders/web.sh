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
for f in control postinst prerm postrm
do
    cp $RESOURCES/$f $DEBIAN
done

# Web repo and python-lib copy
mkdir -p $OPT
mkdir -p $EXTRA
# N.B. (PH) this will not copy dotfiles
cp -r ../src/web/* $OPT
cp -r ../src/python-lib $EXTRA

# Nginx configuration
mkdir -p $NGINX/sites-available $NGINX/sites-enabled
cp $RESOURCES/aerofsconfig $NGINX/sites-available/
ln -s ../sites-available/aerofsconfig $NGINX/sites-enabled/aerofsconfig

# uwsgi configuration
mkdir -p $UWSGI/apps-available $UWSGI/apps-enabled
cp $OPT/production.ini $UWSGI/apps-available/productionAeroFS.ini
ln -s ../apps-available/productionAeroFS.ini $UWSGI/apps-enabled/productionAeroFS.ini

# remove unnecessary files
rm -r $OPT/resources
