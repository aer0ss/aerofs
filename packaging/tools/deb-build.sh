#!/bin/bash

# Get the current version number for a given AeroFS debian package.
# Params:
#   $1 = the name of the debian package.
#   $2 = the destination <staging|prod>.
# Return:
#   0 when the version number exists, on the server, non-zero otherwise.
# Echo:
#   The current version number.
function get_version_number()
{
    wget \
        --output-document $1.current.ver \
        --no-check-certificate -q \
        --no-cache http://apt.aerofs.com/ubuntu/$2/versions/$1.current.ver

    if [ $? -eq 0 ]
    then
        cat $1.current.ver
        rm $1.current.ver
        return 0
    else
        return 1
    fi
}

# Main Script.

# User input - the name of the debian package to be build must be specified.
if [ $# -ne 2 ]
then
    echo "usage: $0 <deb-name> <STAGING|PROD>"
    exit 1
fi

# Make sure there are no spelling errors, i.e. make sure we are able to build this
# package.
cd $(dirname $0)/..
debname=$1
mode=$(echo $2 | tr [A-Z] [a-z])
control=./$debname/DEBIAN/control

test -d $debname
if [ $? -ne 0 ]
then
    echo "Don't know how to build '$debname'."
    exit 1
fi

# Pull the current version number.
version=$(get_version_number aerofs-$debname $mode)

if [ $? -ne 0 ]
then
    # Default version for new packages.
    version=1.0.0
fi

# Create the required control file with new build version to build the debian package.
major=$(echo $version | awk -F'.' '{print $1}')
minor=$(echo $version | awk -F'.' '{print $2}')
build=$(echo $version | awk -F'.' '{print $3}')
build=$[$build+1]
newversion=$major.$minor.$build

echo "Current $debname version is $version. New version is $newversion."
echo $newversion > aerofs-$debname.current.ver
echo "Version: $newversion" >> $control

# And finally build the debian package.
# brew install dpkg
# if you don't have dpkg-deb on your system.
# brew install $HOME/repos/aerofs/tools/fakeroot.rb
# if you lack fakeroot.
tmpdir=$(mktemp -d "$debname-XXXXXX")
# We have to do the copy and chown under the fakeroot environment so the
# package unpacks to files with root's uid/gid.
fakeroot << EOF
set -e
cp -a $debname/* $tmpdir/
chown -R 0:0 ${tmpdir}
dpkg-deb --build ${tmpdir} aerofs-${debname}.deb
EOF

if [ $? -ne 0 ]
then
    echo "Failure in fakeroot. (Do you have dpkg and fakeroot installed?)."
    exit 1
fi

rm -rf ${tmpdir}

# Clean up the control file (remove the version number, i.e. the last line).
cat $control | grep -v "$(cat $control | tail -1)" > $control
