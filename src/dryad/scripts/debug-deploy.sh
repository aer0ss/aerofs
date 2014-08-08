#!/bin/bash
set -eu

cd $(dirname $0)/../../..
ant package_dryad -Dmode=PUBLIC
scp packaging/debs/aerofs-dryad.deb dryad.aerofs.com:~/
ssh -t dryad.aerofs.com "sudo dpkg -i aerofs-dryad.deb; sudo service dryad start"
