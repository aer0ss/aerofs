#!/bin/bash
set -e
set -x

BRIDGE_COUNT=${BRIDGE_COUNT:=1}
CLIENT_COUNT=${CLIENT_COUNT:=2}
WBRIDGE_COUNT=${WBRIDGE_COUNT:=1}
WCLIENT_COUNT=${WCLIENT_COUNT:=2}

ACTOR_POOL_DIR="$(dirname $0)/actor-pool"
VAGRANT_BASE_DIR="$(dirname $(dirname $0))/vagrant"

# Clear the pool
mysql -uroot --password='temp123' < "$ACTOR_POOL_DIR"/actorpool.sql  # clear the db

# Linux actors
pushd "$VAGRANT_BASE_DIR"/syncdet_linux
vagrant destroy --force
vagrant up | tee /var/log/ci/syncdet-vagrant-up.log
bottom=0
top=$(($CLIENT_COUNT - 1))
if [[ $top -ge $bottom ]]; then
    printf "aerofsbuild-vagrant-%i " $(seq $bottom $top) | "$ACTOR_POOL_DIR"/register.py --os l --vm y --isolated n
fi
popd

# Windows actors
pushd "$VAGRANT_BASE_DIR"/syncdet_win
vagrant destroy --force
vagrant up | tee /var/log/ci/win7-syncdet-vagrant-up.log
bottom=0
top=$(($WCLIENT_COUNT - 1))
if [[ $top -ge $bottom ]]; then
    printf "aerofsbuild-win7-vagrant-%i " $(seq $bottom $top) | "$ACTOR_POOL_DIR"/register.py --os w --vm y --isolated n
fi
popd

# OSX actors
echo osx-1 | "$ACTOR_POOL_DIR"/register.py --os o --vm n --isolated n
