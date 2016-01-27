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

# get bridge interface base ip
# TODO: take the netmask into account instead of assuming it to be 255.255.255.0
#
# Configuring dnsmasq across multiple hosts to all forward to the host on which the
# VM resides and then make that work inside docker containers is fragile and painful
# Instead we rely on the presence of the bridge interface and register all actors
# through their bridge ip
#
# TODO: a future iteration of actor provisioning should instead move the registration
# inside the actor itself, allowing the actor pool to determine the public IP of the
# actor...
# This will be especially important when we finally start moving actors off of bigboy
bridge_ip=$(ifconfig $BRIDGE_IFACE | grep 'inet addr:' | cut -d':' -f 2 | cut -d'.' -f 1-3)

# Linux actors
pushd "$VAGRANT_BASE_DIR"/syncdet_linux
vagrant destroy --force
vagrant up
bottom=0
top=$(($CLIENT_COUNT - 1))
popd
if [[ $top -ge $bottom ]]; then
    for i in $(seq $bottom $top) ; do
        # NB: use the hostonly ip to ssh as this one is guaranteed to be static
        ip=$(ssh aerofstest@192.168.50.$(( 10 + i)) ifconfig | grep -F "inet addr:${bridge_ip}." | cut -d':' -f2 | cut -d' ' -f 1)
        rl="${rl:+$rl }$ip"
    done
    echo $rl | "$ACTOR_POOL_DIR"/register.py --os l --vm y --isolated n
fi

# Windows actors
pushd "$VAGRANT_BASE_DIR"/syncdet_win
vagrant destroy --force
vagrant up
bottom=0
top=$(($WCLIENT_COUNT - 1))
popd
if [[ $top -ge $bottom ]]; then
    for i in $(seq $bottom $top) ; do
        # NB: use the hostonly ip to ssh as this one is guaranteed to be static
        ip=$(ssh aerofstest@192.168.50.$(( 110 + i)) ipconfig | grep -F "IPv4 Address" | grep -F " : ${bridge_ip}." | cut -d':' -f2)
        rw="${rw:+$rw }$ip"
    done
    echo $rw | "$ACTOR_POOL_DIR"/register.py --os w --vm y --isolated n
fi

# OSX actors
ip=$(ssh aerofstest@osx-1 ifconfig | grep -F "inet ${bridge_ip}." | cut -d' ' -f 2)
echo $ip | "$ACTOR_POOL_DIR"/register.py --os o --vm n --isolated n
