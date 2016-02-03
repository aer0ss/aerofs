#!/bin/bash

set -e

PWD="$( cd $(dirname $0) ; pwd -P )"

# internal DNS for
#  .docker hostnames
#  transparent proxying of apt repos to apt-cacher-ng
$PWD/rawdns/stop.sh

# start apt-cacher-ng first to make sure it is ready once
# rawdns comes up and redirects apt traffic towards it
$PWD/apt-cacher-ng/stop.sh

# pypi caching
$PWD/devpi/stop.sh

# apk cache
$PWD/alpinx/stop.sh

