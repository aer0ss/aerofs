#!/bin/bash -e

VERSION=0.4
SERVICE=synctime
OPT="$SERVICE/opt/$SERVICE"

TARBALL="$SERVICE-server-$VERSION.tgz"
REPO_URL="http://repos.arrowfs.org/nexus/content/repositories/releases/com/aerofs/$SERVICE/$SERVICE-server/$VERSION"

# Download the distribution tarball into the opt directory
#  1 remove all directories and files other than the yml and base synctime
#  script which should both be there
ls -d $OPT/* | grep -v ${SERVICE}.yml | grep -v "${SERVICE}$" | xargs rm -r
#  2 download the tarball
wget -O $OPT/$TARBALL $REPO_URL/$TARBALL
#  3 extract then rm the tarball
tar -xf $OPT/$TARBALL -C $OPT
mv $OPT/"${TARBALL%.*}" $OPT/$SERVICE-server
rm $OPT/$TARBALL
