#!/bin/bash
set -eu

PWD="$(cd $(dirname $0); pwd -P)"

# start apt-cacher-ng first to make sure it is ready once
# rawdns comes up and redirects apt traffic towards it
$PWD/apt-cacher-ng/start.sh

# Alpine linux package cache
$PWD/alpinx/start.sh

# internal DNS for
#  .docker hostnames
#  transparent proxying of apt repos to apt-cacher-ng
#  transparent proxying of alpine repo to alpinx
$PWD/rawdns/start.sh ${1:-}

# pypi caching
$PWD/devpi/start.sh
