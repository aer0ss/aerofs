#!/bin/bash
set -ex

if [ $# != 1 ]; then
    echo "This script downloads relevant logs from the appliance VM to the host."
    echo "Usage: $0 <log-archive-file-path>"
    exit 11
fi

OUTPUT="$1"

SSH_FORWARD_PORT=54365
THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"
SSH_OPTS="-q -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i ${THIS_DIR}/ci-ssh.key"

# Single quotes around END disables variable expansion in the heredoc
ssh -p ${SSH_FORWARD_PORT} ${SSH_OPTS} core@localhost <<'END'
    rm -rf logs logs.tgz
    mkdir logs
    for i in $(docker ps -aq --no-trunc); do
        NAME=$(docker inspect -f '{{ .Name }}' ${i} | sed -e 's`/``')
        echo "Collecting logs for container ${NAME}..."
        docker logs --timestamps ${NAME} >logs/${NAME}.log 2>&1
    done
    echo "Collecting logs for ship.service..."
    journalctl -u ship >logs/ship.log 2>&1
    tar zcf logs.tgz logs
END

rm -rf "${OUTPUT}"
scp -P ${SSH_FORWARD_PORT} ${SSH_OPTS} core@localhost:logs.tgz "${OUTPUT}"