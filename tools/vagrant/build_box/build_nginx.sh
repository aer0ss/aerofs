#!/bin/bash

set -e
set -x

[ "$(whoami)" = "root" ] || {
    exec sudo -u root "$0" "$@"
    echo Run this script as root. >&2
    exit 1
}

cd "/home/vagrant"
mkdir -p package_source
cd package_source
add-apt-repository --yes ppa:nginx/development
apt-get update
apt-get -y -o DPkg::Options::=--force-confold build-dep nginx
for pkg in init-system-helpers nginx ; do
    apt-get source $pkg
    pushd "$pkg"*
    debuild -i -us -uc -b
    popd
done
cp *.deb /vagrant/
echo "packages ready on host folder"
