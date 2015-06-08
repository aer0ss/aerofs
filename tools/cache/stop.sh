#!/bin/bash

# FIXME: support non-docker-machine dev env

# remove dns config inserted by start.sh
# FIXME: boot2docker is very minimalist so we have to use sed, which is pretty fragile...
docker-machine ssh docker-dev "sudo cat /var/lib/boot2docker/profile | sed \"s/EXTRA_ARGS='--dns 172.17.42.1 /EXTRA_ARGS='/\" | sudo tee cat /var/lib/boot2docker/profile"

# restart docker daemon (the the init script is borked and cannot restart properly...)
docker-machine ssh docker-dev "sudo /etc/init.d/docker stop ; sleep 5 ; sudo /etc/init.d/docker start"

docker stop rawdns
docker rm --force rawdns

docker stop apt-cacher-ng
docker rm --force apt-cacher-ng

