#!/bin/bash

docker stop rawdns
docker rm --force rawdns

# remove dns config inserted by start.sh
# FIXME: boot2docker is very minimalist so we have to use sed, which is pretty fragile...
docker-machine ssh docker-dev "grep -v -F 'EXTRA_ARGS=\"--dns 172.17.42.1 \$EXTRA_ARGS\"' /var/lib/boot2docker/profile | sudo tee /var/lib/boot2docker/profile"

# restart docker daemon (the the init script is borked and cannot restart properly...)
docker-machine ssh docker-dev "sudo /etc/init.d/docker stop ; sleep 5 ; sudo /etc/init.d/docker start"

