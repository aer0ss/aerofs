#!/bin/bash
set -eu

tempdir=$(mktemp -d /tmp/aerofs-db-backup.XXXXXXXXX)
rm -rf $tempdir
mkdir -p $tempdir/aerofs-db-backup
pushd $tempdir 1>/dev/null 2>/dev/null

if [ -f /var/aerofs/external-db-flag ]
then
    cp -a /var/aerofs/external-db-flag aerofs-db-backup/external-db-flag
else
    # These will all be persisted in the external db, no need to include in backup
    echo ">>> Backing up redis database..."
    if [ ! -f /data/redis/redis.aof ]
    then
        touch /data/redis/redis.aof
    fi
    cp -a /data/redis/redis.aof aerofs-db-backup/redis.aof

    echo ">>> Backing up mysql database..."
    # unfortunately mysql really really sucks when it comes to UTF-8 handling
    # it appears that the only way to avoid silent corruption of non-BMP characters
    # during backup is to export character data as hex blobs...
    mysqldump --hex-blob --default-character-set=binary -h mysql.service --events --all-databases > aerofs-db-backup/mysql.dump

    # To mark that the polaris db from this appliance is stable enough to not be dropped
    touch aerofs-db-backup/restore-polaris

    echo ">>> Backing up CA files..."
    mkdir -p aerofs-db-backup/ca-files
    touch aerofs-db-backup/ca-files/migrated
fi

echo ">>> Backing up configuration properties..."
cp -a /opt/config/properties/external.properties aerofs-db-backup/external.properties

for svc in charlie waldo
do
    echo ">>> Backing up $svc database..."
    cp -a /data/$svc aerofs-db-backup/$svc
done

echo ">>> Creating backup file..."
# FIXME: does this comment still make sense now that we use alpine?
# gnu tar does not handle reading sparse files efficiently using SEEK_HOLE
# bsd tar does not handle extracting sparse files efficiently and fills all the holes
# bsd tar also doesn't support producing sparse archives at all until 3.0.4S.
#   Ubuntu 12.04 ships version 3.0.3. :(
# takeaway: use bsdtar on sufficiently up-to-date systems.
#           use gnu tar on older stuff, like this appliance.
tar -Sczf aerofs-db-backup.tar.gz aerofs-db-backup/*

popd 1>/dev/null 2>/dev/null
cp -a $tempdir/aerofs-db-backup.tar.gz .
rm -rf $tempdir

echo ">>> AeroFS appliance data backed up to $(pwd)/aerofs-db-backup.tar.gz"
