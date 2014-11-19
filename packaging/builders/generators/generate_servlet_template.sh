#!/bin/bash
set -e -u

if [[ $# -ne 3 || "$1x" == "x" || "$2x" == "x" || "$3x" == "x" ]]
then
    echo "usage: $0 [servlet_name] [base_dir] [proj_dir]"
    echo
    echo "example: $0 sv ~/repos/aerofs aerofs.spsv"
    exit 2
fi

SERVLET_NAME="$1"
BASEDIR="$2"
PROJDIR="$3"

PACKAGE_NAME="$SERVLET_NAME"
BUILDROOT="build/$PACKAGE_NAME"

RESOURCES="$BASEDIR"/src/"$PROJDIR"/resources/"$SERVLET_NAME"
INSTALL="$BUILDROOT"/usr/share/aerofs-"$PACKAGE_NAME"/"$SERVLET_NAME"/WEB-INF
CONTEXT="$BUILDROOT"/etc/tomcat6/Catalina/localhost
DEBIAN="$BUILDROOT"/DEBIAN

mkdir -p "$BUILDROOT"
mkdir -p "$INSTALL/lib"
mkdir -p "$INSTALL/classes"
mkdir -p "$CONTEXT"
mkdir -p "$DEBIAN"

# debian files
for F in control
do
    cp -R "$RESOURCES"/$F "$DEBIAN"
done

# Jars and dependency jars
cp -R "$BASEDIR"/out.gradle/spsv/dist/* "$INSTALL/lib/"

# Add configuration properties if not deploying to public or CI (mild hack)
if [ "$BIN" != "PUBLIC" -a "$BIN" != "CI" ] ; then
    echo "config.loader.is_private_deployment=true" > $INSTALL/classes/configuration.properties
fi

# web.xml (specifies servlet entry point, among other things)
cp -a "$RESOURCES"/web.xml "$INSTALL"/
