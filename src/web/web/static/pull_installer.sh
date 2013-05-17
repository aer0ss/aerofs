#!/bin/bash -e
cd $(dirname $0)

for file in \
    AeroFSInstall.dmg \
    AeroFSInstall.exe \
    AeroFSTeamServerInstall.dmg \
    AeroFSTeamServerInstall.exe \
    aerofs-installer.deb \
    aerofsts-installer.deb \
    aerofsts-installer.tgz \
    current.ver
do
    if [ ! -f $file ]
    then
        wget --no-check-certificate https://nocache.client.aerofs.com/$file
    fi
done

echo "Client binaries successfully downloaded."
