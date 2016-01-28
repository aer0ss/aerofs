#!/bin/bash

if [ $# -ne 1 ]
then
    echo "Usage: $0 <private IP of server>"
    exit 1
fi

IP="$1"
PWD=$(dirname "${BASH_SOURCE[0]:-$0}")

scp -i "$PWD"/secrets/hpc-key.pem "$PWD"/data/server_bootstrap.sh "$PWD"/secrets/aerofs.com.crt "$PWD"/secrets/aerofs.com.key core@$IP:~
ssh -i "$PWD"/secrets/hpc-key.pem core@$IP "sudo /home/core/server_bootstrap.sh"
