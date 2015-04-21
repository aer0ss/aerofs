#!/bin/bash
set -u -x

function error_handler {
    echo Error at line $1
    exit 2
}

trap 'error_handler $LINENO' ERR

function DieUsage {
    echo Usage: $0 >&2
    exit 1
}

[[ $# -eq 0 ]] || DieUsage

fwport=2122
SSH_OPTS="-o Loglevel=FATAL -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes"
LOGS=appliance-logs.zip

echo Archiving appliance logs...
ssh -p $fwport $SSH_OPTS ubuntu@localhost <<EOSSH

sudo aerofs-bootstrap-taskfile /opt/bootstrap/tasks/archive-logs.tasks

EOSSH

rm -rf $LOGS
scp -P $fwport $SSH_OPTS ubuntu@localhost:/opt/bootstrap/public/logs.zip $LOGS

