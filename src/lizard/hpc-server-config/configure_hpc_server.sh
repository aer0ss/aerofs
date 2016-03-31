#!/bin/bash

if [ $# -ne 1 ]
then
    echo "Usage: $0 <private IP of server>"
    exit 1
fi

IP="$1"
PWD=$(dirname $0)

scp -o StrictHostKeyChecking=no -i "$PWD"/secrets/hpc-key.pem "$PWD"/secrets/aerofs.com.crt "$PWD"/secrets/aerofs.com.key \
"$PWD"/secrets/aws_credentials "$PWD"/data/server_bootstrap.sh "$PWD"/data/server_pull.sh core@$IP:~
ssh -o StrictHostKeyChecking=no -i "$PWD"/secrets/hpc-key.pem core@$IP "sudo /home/core/server_pull.sh && sudo /home/core/server_bootstrap.sh"
