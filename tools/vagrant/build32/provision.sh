#!/bin/bash

# Update base system, install packages useful for building packages
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y -o DPkg::Options::=--force-confold dist-upgrade

# need headers to build vbox driver
apt-get -y install linux-headers-$(uname -r)
# recompile vbox driver after kernel upgrade
/etc/init.d/vboxadd setup

apt-get build-dep -y openssl openjdk-6-jdk
apt-get install -y -o DPkg::Options::=--force-confold  \
       vim-nox swig qt4-qmake git-core openjdk-6-jdk
