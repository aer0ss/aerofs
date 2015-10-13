#!/bin/bash
set -eu

function usage()
{
    echo "Usage: $0 <aerofs_db_backup_file>"
}

function malformed_db_backup()
{
    echo "ERROR: Malformed db backup file: $@."
}

function drop_and_restore_db()
{
    echo ">>>   Drop $1..."
    echo "drop database if exists \`$1\`; " | mysql -h mysql.service
    echo ">>>   Restoring $1..."
    mysql -h mysql.service <<< "create database $1"
    mysql -h mysql.service -D $1 -o $1 < aerofs-db-backup/mysql.dump
}

if [ $# -ne 1 ]
then
    usage
    exit 1
fi

file="$1"

if [ ! -f "$file" ]
then
    echo "ERROR: \"$file\" does not exist."
    usage
    exit 2
fi

echo ">>> Reading backup file..."

tempdir=$(mktemp -d /tmp/aerofs-db-backup.XXXXXXXXX)
cp -a "$file" "$tempdir"

pushd $tempdir 1>/dev/null 2>/dev/null
# gnu tar does not handle reading sparse files efficiently using SEEK_HOLE
# bsd tar does not handle extracting sparse files efficiently and fills all the holes
# takeaway: create tars with bsdtar, and extract them with gnutar
# NOTE: In case the backup file came from 0.8.63 through 0.8.68 inclusive,
# we will deliberately ignore any topics that live in /data/topics.
#           Let this monument stand for all time.
tar --extract --sparse --exclude aerofs-db-backup/topics -f $file

if [ ! -d aerofs-db-backup ]
then
    malformed_db_backup "expected top level directory \"aerofs-db-backup\""
    exit 3
fi

if [ ! -f aerofs-db-backup/external-db-flag ]
then
    # no need to have these in the backup file since they'll all be in the offsite backup
    if [ ! -f aerofs-db-backup/redis.aof ]
    then
        malformed_db_backup "expected file \"aerofs-db-backup/redis.aof\" not found"
        exit 4
    fi

    if [ ! -f aerofs-db-backup/mysql.dump ]
    then
        malformed_db_backup "expected file \"aerofs-db-backup/mysql.dump\" not found"
        exit 5
    fi

    if [ ! -d aerofs-db-backup/ca-files ]
    then
        malformed_db_backup "expected dir \"aerofs-db-backup/ca-files\" not found"
        exit 6
    fi
fi

if [ ! -f aerofs-db-backup/external.properties ]
then
    malformed_db_backup "expected dir \"aerofs-db-backup/external.properties\" not found"
    exit 7
fi

if [ -f aerofs-db-backup/external-db-flag ]
then
    mkdir -p /var/aerofs
    cp -a aerofs-db-backup/external-db-flag /data/bunker/external-db-flag
else
    echo ">>> Restoring redis database..."
    rm -f /data/redis/redis.*
    cp -a aerofs-db-backup/redis.aof /data/redis

    # NOTE: This is not a general solution. Required because mysql.dump only deletes
    # and restores tables that existed when the dump-file was created. This will cause
    # problems when Flyway makes non-idempotent changes to such tables.
    echo ">>> Clearing flyway-managed databases..."
    for db in bifrost aerofs_sp polaris
    do
        drop_and_restore_db $db
    done

    # We need the mysql schema for the migration.sh, so we only drop aerofs_ca if
    # the CA has already been migrated
    if [ -e aerofs-db-backup/ca-files/migrated ]
    then
        drop_and_restore_db aerofs_ca
    else
        echo ">>> Migrating CA files..."
        /opt/bunker/migration.sh
    fi

    # As a compromise between CI and packaging complexity, we ship polaris
    # in the private cloud appliance. It is running but clients do not
    # attempt to talk to it and nginx doesn't proxy requests to it.
    # To preserve maximum schema flexibility we explictly drop the polaris
    # db from backups.
    if [ ! -e aerofs-db-backup/restore-polaris ]
    then
        echo ">>> Dropping database polaris created before schema stabilized..."
        echo "drop database if exists \`polaris\`; create database \`polaris\`;" | mysql -h mysql.service
    fi

fi

# Only restore charlie db for backups that are new enough to contain it
if [ -d aerofs-db-backup/charlie ] ; then
    rm -rf /data/charlie
    cp -a aerofs-db-backup/charlie /data/charlie
fi

echo ">>> Restoring configuration properties..."
PROPS=/opt/config/properties/external.properties

# The string replacement is to support restoring from legacy appliances
sed -e "s/email_host=localhost/email_host=postfix.service/" aerofs-db-backup/external.properties > ${PROPS}

# Add default properties specific to the dockerize appliance if they aren't present
for i in $(cat /external.properties.docker.default); do
    KEY=$(echo "$i" | sed -e "s/=.*//")
    if [ -z "$(grep "^${KEY}=" ${PROPS})" ]; then
        echo "${i}" >> ${PROPS}
    fi
done

popd 1>/dev/null 2>/dev/null

echo ">>> AeroFS databases successfully restored."
