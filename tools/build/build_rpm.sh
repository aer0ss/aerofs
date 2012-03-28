#!/bin/bash
#This scripts generate the 32 or 64 deb installation packages
if [ x"$1" == x ]; then
    echo usage '$0 [32|64]'
    exit
fi

echo building .rpm package
#define variables
name='aerofs'
read -r version < ../aerofs.release/aerofs/aerofs.ver
release='1'
#read the arch type
uname -m > archtemp
read -r arch < archtemp
rm archtemp

if [ $1 == 32 ]; then
    arch='i386'
    buildfolder='aerofs.linux32'
else
    #arch='amd64'
    buildfolder='aerofs.linux64'
fi
group='Air Computing'
license='unknown'
maintainer='Alex Liu'
descrip='A file system tools to sync your files without servers'
url='www.aerofs.com'
install_dir=/opt


#Generate .macro file
echo Generating .macro file
echo %_topdir ~/rpmrelease/aerofs >> .rpmmacros
echo %_builder %{_topdir}/BUILD >> .rpmmacros
echo %_rpmdir %{_topdir}/RPMS  >> .rpmmacros
echo %_sourcedir %{_topdir}/SOURCES  >> .rpmmacros
echo %_specdir %{_topdir}/SPECS >> .rpmmacros
echo %_srcrpmdir %{_topdir}/SRPMS >> .rpmmacros

#Generate aerofs.spec file
echo Name: $name >> aerofs.spec
echo Version: $version >> aerofs.spec 
echo Release: $release >> aerofs.spec 
echo Summary: Sync file without servers >> aerofs.spec
echo Group: $group >> aerofs.spec
echo License: $license >> aerofs.spec
echo URL: $url >> aerofs.spec
echo '' >>aerofs.spec
echo '%description' >>aerofs.spec
echo %pre >> aerofs.spec
echo %build >> aerofs.spec
echo %install >> aerofs.spec
echo %clean >> aerofs.spec
echo %files >> aerofs.spec
echo '%defattr(-,root,root)' >> aerofs.spec
echo %doc >> aerofs.spec
echo $install_dir >> aerofs.spec
echo %changelog >> aerofs.spec
echo %post >> aerofs.spec
echo "rm /usr/local/bin/aerofs" >> aerofs.spec
echo "ln -s /opt/aerofs/aerofsgui /usr/local/bin/aerofs" >> aerofs.spec
echo "cp /opt/aerofs/aerofs /etc/init.d/" >> aerofs.spec 
echo "update-rc.d aerofs defaults" >> aerofs.spec 
echo "cd /opt/aerofs" >> aerofs.spec
echo "chmod a+x jvmldconf.sh" >> aerofs.spec
echo "./jvmldconf.sh" >> aerofs.spec 


#Make rmp building directories.
pushd ~/
rm -Rf rpmrelease
mkdir -p rpmrelease/aerofs
cd rpmrelease/aerofs
mkdir BUILD
mkdir RPMS
mkdir SOURCES
mkdir SPECS
mkdir SRPMS
mkdir -p BUILDROOT/$name'-'$version'-'$release'.'$arch$install_dir/aerofs

#copy all the files in aerofs and aerofs.linux?? to user/local/bin
popd
cp -rf ../aerofs.release/aerofs/* ~/rpmrelease/aerofs/BUILDROOT/$name'-'$version'-'$release'.'$arch$install_dir/aerofs
cp -rf ../aerofs.release/$buildfolder/* ~/rpmrelease/aerofs/BUILDROOT/$name'-'$version'-'$release'.'$arch$install_dir/aerofs
cp .rpmmacros ~/
cp aerofs.spec ~/
rm .rpmmacros
rm aerofs.spec

#option script:

#Generate .rpm file
pushd ~/
find ./rpmrelease -type d | xargs chmod 755
rpmbuild -bb aerofs.spec
popd
cp ~/rpmrelease/aerofs/RPMS/$arch/$name'-'$version'-'$release'.'$arch'.rpm' ../aerofs.release/$name'-'$version'-'$arch'.rpm'


