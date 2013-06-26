#!/bin/bash -e
cd $(dirname $0)
rm -f *.dmg *.deb *.tgz *.exe *.ver *.zip

WEB_ROOT=nocache.client.enterprise.aerofs.com

for file in \
    AeroFSInstall.dmg \
    aerofs-installer.deb \
    aerofs-installer.tgz \
    AeroFSInstall.exe \
    AeroFSTeamServerInstall.dmg \
    AeroFSTeamServerInstall.exe \
    aerofsts-installer.deb \
    aerofsts-installer.tgz \
    aerofsts-x86_64.tgz \
    aerofsts-x86.tgz \
    aerofs-x86_64.tgz \
    aerofs-x86.tgz \
    current.ver
do
    echo ">>> Download $file..."
    wget --quiet --no-check-certificate https://$WEB_ROOT/$file
done

version=$(cat current.ver | awk -F'=' '{print $2}')

for file in \
    aerofs-${version}-x86_64.tgz \
    aerofs-${version}-x86.tgz \
    AeroFSInstall-${version}.dmg \
    AeroFSInstall-${version}.exe \
    aerofs-osx-${version}.zip \
    AeroFSTeamServerInstall-${version}.dmg \
    AeroFSTeamServerInstall-${version}.exe \
    aerofsts-${version}-x86_64.tgz \
    aerofsts-${version}-x86.tgz \
    aerofsts-osx-${version}.zip
do
    echo ">>> Download $file..."
    wget --quiet --no-check-certificate https://$WEB_ROOT/$file
done

echo "Client binaries successfully downloaded."
