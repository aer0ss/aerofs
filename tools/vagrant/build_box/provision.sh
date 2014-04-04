#!/bin/bash

# Ensure we're using the apt-cacher-ng proxy
echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy

# Update base system, install packages useful for building packages
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y -o DPkg::Options::=--force-confold dist-upgrade
apt-get install -y -o DPkg::Options::=--force-confold  \
            build-essential git-core vim-nox mercurial \
            devscripts python-software-properties
