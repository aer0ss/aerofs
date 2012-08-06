#!/bin/bash
#
# Script to generate SQL required to gather data from existing certificates and
# add it to the database.

if [ $# -ne 1 ]
then
    echo "usage: $0 <dirname>"
    exit 1
fi

dirname=$1

for file in $(ls $dirname/*.cert 2>/dev/null)
do
    expire=$(echo "$(openssl x509 -noout -in $file -dates)" \
        | grep -i "notafter" \
        | awk -F= '{print $2}')

    serial=$(echo $(openssl x509 -noout -in $file -serial) \
        | awk -F= '{print $2}')

    date=$(date -d "$expire" -u "+%Y-%m-%d %H:%M:%S")

    did=$(basename $file | awk -F- '{print $2}' | awk -F'.cert' '{print $1}')

    echo "insert into sp_cert set c_serial=$serial, c_expire_ts=\"$date\", c_revoke_ts=0, c_device_id=\"$did\";"
done
