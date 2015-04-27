#!/bin/bash
set -eux

# Ensure we're using the apt-cacher-ng proxy
echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy

echo $(whoami)
apt-get update && apt-get install -y git
git clone https://git.openstack.org/openstack-dev/devstack
./devstack/tools/create-stack-user.sh
rm -rf devstack
sudo -u stack /vagrant/stack-provision.sh
