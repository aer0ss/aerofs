#!/bin/bash

if [ $# -ne 1 ]
then
    echo "Usage: $0 <private IP of server>"
    exit 1
fi

IP="$1"
PWD=$(dirname $0)

scp -o StrictHostKeyChecking=no -i /opt/lizard/hpc-server-config/secrets/hpc-key.pem /opt/lizard/hpc-server-config/secrets/aerofs.com.crt /opt/lizard/hpc-server-config/secrets/aerofs.com.key \
/opt/lizard/hpc-server-config/secrets/aws_credentials "$PWD"/server_bootstrap.sh "$PWD"/server_pull.sh core@$IP:~
ssh -o StrictHostKeyChecking=no -i /opt/lizard/hpc-server-config/secrets/hpc-key.pem core@$IP "sudo /home/core/server_pull.sh && sudo /home/core/server_bootstrap.sh"
