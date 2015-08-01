#!/bin/bash

docker stop rawdns
docker rm --force rawdns

VM=${1:-$(docker-machine active 2>/dev/null || echo "docker-dev")}

if docker-machine ls "$VM" &>/dev/null ; then
    profile=/var/lib/boot2docker/profile
    service=/etc/systemd/system/docker.service
    
    # detect old boot2docker vs new b2d-ng from docker-machine 0.4+
    os=$(docker-machine ssh $VM "if [ -f $service ] ; then \
        echo b2d-ng ; elif [ -f $profile ] ; then \
        echo b2d ; else \
        echo unsupported ; fi")

    echo "detected $VM as $os"

    # remove dns config inserted by start.sh
    if [[ "$os" == "b2d" ]] ; then
        docker-machine ssh $VM "grep -v -F 'EXTRA_ARGS=\"--dns 172.17.42.1' $profile | sudo tee ${profile}.old ; \
            sudo mv ${profile}.old ${profile}"

        # restart docker daemon (the the init script is borked and cannot restart properly...)
        echo "restarting docker daemon"
        docker-machine ssh $VM "sudo /etc/init.d/docker stop ; sleep 5 ; sudo /etc/init.d/docker start"
    elif [[ "$os" == "b2d-ng" ]] ; then
        docker-machine ssh $VM "cat $service | sed 's/\\\$EXTRA_ARGS//' | sudo tee ${service}.old ; \
            sudo mv ${service}.old ${service} ; \
            sudo rm -f ${service}.d/dns.conf"

        echo "restarting docker daemon"
        docker-machine ssh $VM "sudo systemctl daemon-reload && sudo systemctl restart docker"
    else
        echo "unsupported OS"
        exit 1
    fi
else
    echo "dns config must be manually fixed on raw docker"
fi
