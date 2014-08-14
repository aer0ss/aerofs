#!/bin/bash

# Ensure we're using the apt-cacher-ng proxy
echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy

# Update base system, install packages useful for building packages
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y -o DPkg::Options::=--force-confold dist-upgrade

# need headers to build vbox driver
apt-get -y install linux-headers-$(uname -r)
# recompile vbox driver after kernel upgrade
/etc/init.d/vboxadd setup

apt-get install -y -o DPkg::Options::=--force-confold \
            build-essential git-core bzr vim-nox mercurial \
            devscripts python-software-properties openjdk-7-jdk
