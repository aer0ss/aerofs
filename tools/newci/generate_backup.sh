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
BACKUPS=~/backups

echo Generating backup file...
ssh -p $fwport $SSH_OPTS ubuntu@localhost <<EOSSH

sudo aerofs-bootstrap-taskfile /opt/bootstrap/tasks/db-backup.tasks
sudo aerofs-bootstrap-taskfile /opt/bootstrap/tasks/maintenance-exit.tasks

EOSSH

mkdir -p $BACKUPS/commits

echo Downloading backup file for $commit...
commit="$(git rev-parse HEAD)"
scp -P $fwport $SSH_OPTS ubuntu@localhost:/opt/bootstrap/public/aerofs-db-backup.tar.gz $BACKUPS/commits/$commit.tar.gz

