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

apt-get build-dep -y openssl
apt-get install -y -o DPkg::Options::=--force-confold  \
       vim-nox swig qt4-qmake git-core ant openjdk-6-jdk
