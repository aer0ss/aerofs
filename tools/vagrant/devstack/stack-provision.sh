#!/bin/bash
set -eux
cd ~stack
git clone https://git.openstack.org/openstack-dev/devstack

set +x
echo "DevStack repo cloned."
echo "To finish provisioning, run /opt/stack/devstack/stack.sh as the stack user."
