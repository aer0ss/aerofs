#!/bin/bash
set -ex

if [ $# != 3 ]; then
    echo "Usage: $0 {backup,restore} <backup-file> <done-file>"
    echo "       <backup-file>: the backup file to be saved to or restored from."
    echo "       <done-file>: the path to a file that this script will touch on success."
    exit 11
fi

CMD="$1"
BACKUP_FILE="$2"
DONE_FILE="$3"

rm -rf "${DONE_FILE}"

if [ "${CMD}" = restore ]; then
    "$(dirname $0)/restore.sh" "$2"

else
    # TODO (WW) Refactor backup.sh. Pass in backup file and use command line opions instead of pwd.
    if [ "$(basename "${BACKUP_FILE}")" != "aerofs-db-backup.tar.gz" ]; then
        echo "ERROR: current backup script only support aerofs-db-backup.tar.gz as file name."
        exit 22
    fi

    DIR="$(dirname "${BACKUP_FILE}")"
    mkdir -p "${DIR}"
    pushd "${DIR}" > /dev/null
    "$(dirname $0)/backup.sh"
    popd > /dev/null
fi

mkdir -p "$(dirname "${DONE_FILE}")"
touch "${DONE_FILE}"
