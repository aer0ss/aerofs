#!/bin/bash

# This is temporary; this should be a makefile, or an actually packaged python thing.
# But I'm out of time to do either of these things.

DESTNAME=aero-oauth
BUILDDIR=$(mktemp -d build_XXXXXX)
cp Readme.md site.cfg.template main.py $BUILDDIR

cp -R ../../src/python-lib/aerofs_common $BUILDDIR
cp -R ../../src/python-lib/aerofs_sp $BUILDDIR

rm -rf $DESTNAME
mv $BUILDDIR $DESTNAME
tar czf $DESTNAME.tgz $DESTNAME

echo "Build complete."

