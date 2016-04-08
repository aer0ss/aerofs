#!/bin/bash
set -eu

REPACKAGING=/opt/repackaging
INSTALLERS=/opt/repackaging/installers
TOOLS=/opt/repackaging/tools

REPACKAGE_DONE="$INSTALLERS/modified/.repackage-done"
SITE_PROP="$INSTALLERS/site-config.properties"
# Save the old prop file to persistent storage
SITE_PROP_OLD="$INSTALLERS/modified/.site-config.properties"

function usage()
{
    echo "Usage: $0 <configuration_service_url> <base_ca_certificate_file>"
}

if [ $# -ne 2 ]
then
    usage
    exit 1
fi

configuration_service_url=$1
base_ca_certificate_file=$2

if [ ! -f "$base_ca_certificate_file" ]
then
    echo "$base_ca_certificate_file does not exist."
    usage
    exit 2
fi

# Create the site configuration file with cacert and configuration service url.
echo "config.loader.configuration_service_url=$configuration_service_url" > $SITE_PROP
echo -n "config.loader.base_ca_certificate=" >> $SITE_PROP

IFS='
'
for line in $(cat $base_ca_certificate_file)
do
    echo -n "$line\n" >> $SITE_PROP
done

# If the files are the same AND the repackaging done file exists, we have
# nothing to do.
if diff -q "$SITE_PROP_OLD" "$SITE_PROP" && [ -f "$REPACKAGE_DONE" ]
then
    echo "Repackaging already done. Exiting with code 0."
    exit 0
fi

# It's necessary to remove the done flag otherwise the script would misbehave if:
#
# - The 1st run succeeded.
# - The 2nd run generated a different prop file and then failed.
# - The 3rd run generated the same prop file as the 2nd run, and thus skipped building and returned
#   success.
#
rm -f "$REPACKAGE_DONE"

mkdir -p "$(dirname $SITE_PROP_OLD)"
cp "$SITE_PROP" "$SITE_PROP_OLD"

rm -rf $INSTALLERS/modified/*
mkdir -p $INSTALLERS/modified
version="$(cat $INSTALLERS/original/current.ver | awk -F'=' '{print $2}')"
echo "Repackaging version $version:"

if [ -f $INSTALLERS/original/eyja/Eyja-$version.dmg ]; then
    echo "Repackaging Eyja OSX installer..."
    $TOOLS/osx/add_file_to_image.sh \
        $INSTALLERS/original/eyja/Eyja-$version.dmg \
        $INSTALLERS/modified/Eyja-$version.dmg \
        $SITE_PROP \
        /Eyja.app/Contents/Resources/site-config.properties # sibling to app.asar
else
    echo "Skipping missing Eyja OSX installer..."
fi

for arch in ia32 x64
do
    if [ -f $INSTALLERS/original/eyja/eyja-linux-$arch-$version.tar.bz2 ]; then
        echo "Repackaging Eyja Linux $arch installer..."
        $TOOLS/linux/add_file_to_tgz.sh \
            $INSTALLERS/original/eyja/eyja-linux-$arch-$version.tar.bz2 \
            $INSTALLERS/modified/eyja-linux-$arch-$version.tar.gz \
            $SITE_PROP \
            eyja-linux-$arch/resources/site-config.properties # sibling to app.asar
    else
        echo "Skipping missing Eyja Linux $arch installer..."
    fi
done

# TODO: windows zip

echo "Repackaging Windows installers..."
cd $TOOLS/win
for program in AeroFS AeroFSTeamServer
do
    $TOOLS/win/build_installer.sh \
        -i ${program}Exec \
        -f $SITE_PROP \
        -f $INSTALLERS/original/${program}Install-${version}.exe \
        -x ${program}Install-${version}.exe \
        -o $INSTALLERS/modified/${program}Install-${version}.exe &

    "${TOOLS}/win/inject_msi.sh" \
       "${SITE_PROP}" \
       "${INSTALLERS}/original/${program}Install-${version}.msi" \
       "${INSTALLERS}/modified/${program}Install-${version}.msi" \
       "${TOOLS}/win/${program}_workspace" &
done

echo "Repackaging OSX installers..."
cd $TOOLS/osx
for program in AeroFS AeroFSTeamServer
do
    # DMG
    $TOOLS/osx/add_file_to_image.sh \
        $INSTALLERS/original/${program}Install-${version}.dmg \
        $INSTALLERS/modified/${program}Install-${version}.dmg \
        $SITE_PROP \
        /${program}.app/Contents/Resources/site-config.lproj/locversion.plist &

    # updater zip
    case $program in
        AeroFS)           package="aerofs"   ;;
        AeroFSTeamServer) package="aerofsts" ;;
        *)
            echo "I don't have a package name mapping for $program"
            exit 1
            ;;
    esac
    $TOOLS/osx/add_file_to_zip.sh \
        $INSTALLERS/original/${package}-osx-${version}.zip \
        $INSTALLERS/modified/${package}-osx-${version}.zip \
        $SITE_PROP \
        Release/${program}.app/Contents/Resources/site-config.lproj \
        /locversion.plist &
done

echo "Repackaging Linux installers..."
for package in aerofs aerofsts
do
    # Installer .deb (unified)
    $TOOLS/linux/add_file_to_deb.sh \
        $INSTALLERS/original/${package}-installer-${version}.deb \
        $INSTALLERS/modified/${package}-installer-${version}.deb \
        $SITE_PROP \
        usr/share/${package} &

    # Note the output path is "aerofs" even for team server
    # because that's what the updater currently expects and
    # it's hard to change without breaking compat.
    # Installer .tgz (unified)
    $TOOLS/linux/add_file_to_tgz.sh \
        $INSTALLERS/original/${package}-installer-${version}.tgz \
        $INSTALLERS/modified/${package}-installer-${version}.tgz \
        $SITE_PROP \
        aerofs &

    # 64-bit update package
    $TOOLS/linux/add_file_to_tgz.sh \
        $INSTALLERS/original/${package}-${version}-x86_64.tgz \
        $INSTALLERS/modified/${package}-${version}-x86_64.tgz \
        $SITE_PROP \
        aerofs &

    # 32-bit update package
    $TOOLS/linux/add_file_to_tgz.sh \
        $INSTALLERS/original/${package}-${version}-x86.tgz \
        $INSTALLERS/modified/${package}-${version}-x86.tgz \
        $SITE_PROP \
        aerofs &

done

# We spawned all 4 OSX repackage jobs and all 8 Linux package jobs in parallel.
# Await their successful completion before proceeding.
# Note that "wait" always returns 0, so we should check each task's exit code.
FAIL=0
for job in $(jobs -p)
do
    wait $job || let "FAIL+=1"
done
if [ "$FAIL" != "0" ] ; then
    echo "Failed $FAIL of the repackaging tasks."
    exit $FAIL
fi

echo "Making symlinks to versioned packages for unversioned paths..."
pushd $INSTALLERS/modified > /dev/null
# Eyja
ln -s Eyja-$version.dmg                      Eyja.dmg
ln -s eyja-linux-ia32-$version.tar.gz        eyja-linux-ia32.tar.gz
ln -s eyja-linux-x64-$version.tar.gz         eyja-linux-x64.tar.gz
# Windows
ln -s AeroFSInstall-${version}.exe           AeroFSInstall.exe
ln -s AeroFSTeamServerInstall-${version}.exe AeroFSTeamServerInstall.exe
ln -s AeroFSInstall-${version}.msi           AeroFSInstall.msi
ln -s AeroFSTeamServerInstall-${version}.msi AeroFSTeamServerInstall.msi
# OSX (installers + update packages)
ln -s AeroFSInstall-${version}.dmg           AeroFSInstall.dmg
ln -s AeroFSTeamServerInstall-${version}.dmg AeroFSTeamServerInstall.dmg
ln -s aerofs-osx-${version}.zip              aerofs-osx.zip
ln -s aerofsts-osx-${version}.zip            aerofsts-osx.zip
# Linux installers
ln -s aerofs-installer-${version}.deb        aerofs-installer.deb
ln -s aerofs-installer-${version}.tgz        aerofs-installer.tgz
ln -s aerofsts-installer-${version}.deb      aerofsts-installer.deb
ln -s aerofsts-installer-${version}.tgz      aerofsts-installer.tgz
# Linux updates
ln -s aerofs-${version}-x86.tgz              aerofs-x86.tgz
ln -s aerofs-${version}-x86_64.tgz           aerofs-x86_64.tgz
ln -s aerofsts-${version}-x86.tgz            aerofsts-x86.tgz
ln -s aerofsts-${version}-x86_64.tgz         aerofsts-x86_64.tgz
# Version file
# Actually, we should copy this, in case we ever allow updating the originals
# in-place...
cp ../original/current.ver                   current.ver
popd > /dev/null

# Touch the repackage done file so we don't repackage the installers every time
# we run bootstrap.
touch "$REPACKAGE_DONE"
