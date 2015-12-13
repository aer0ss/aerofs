#!/bin/bash
#
# This script has the same affect as clicking the "Download Backup File" button on the
# "/admin/backup_and_upgrade" page. The appliance is put into maintenance mode, a backup file of
# the database is created, the appliance exits maintenance mode, and then the backup file is saved
# to the current directory. The script takes two arguments - the base uri of your appliance, i.e.
# "share.acme.com", and the path to your license file (used for authentication).

if [[ $# -ne 2 || ! -f $2 ]]
then
    echo "Usage:"
    echo "    $(basename $0) <appliance_url> <license_file_path>"
    echo "Example:"
    echo "    $(basename $0) share.acme.com ./aerofs.license"
    exit 1
fi

echo "Executing backup script with host: $1 and license path: $2"
APPLIANCE=$1
LICENSE=$2

LICENSE_SHASUM=$(shasum -a1 "$LICENSE" | cut -d ' ' -f 1)

GET_RESPONSE=$(curl --silent -i https://$1/admin/login)
CSRF_TOKEN=$(echo "$GET_RESPONSE" | grep "csrf-token"| cut -d '"' -f 4)
COOKIE=$(echo "$GET_RESPONSE" | grep "Set-Cookie")

# Curl with silence, and provide the cookie.
function DoGet
{
    curl --silent -H "Cache-Control: no-cache" --cookie "$COOKIE" $@ > /dev/null
    return $?
}

function DoPost
{
    curl -X POST --silent -H "Cache-Control: no-cache" --cookie "$COOKIE" $@ > /dev/null
    return $?
}

echo "Logging into admin site with provided license."

DoPost https://${APPLIANCE}/admin/login_submit \
    --form "license=@$LICENSE" \
    --form "csrf_token=$CSRF_TOKEN"

echo "Entering maintenance mode."

# Boot into maintenance mode
DoPost https://${APPLIANCE}/admin/json-boot/maintenance?license_shasum=${LICENSE_SHASUM} \
    --form "csrf_token=$CSRF_TOKEN"

# Wait for boot into maintenance mode to finish
while [ 1 ]; do
    DoGet --fail https://${APPLIANCE}/admin/json-get-boot?license_shasum=${LICENSE_SHASUM}
    if [ $? -eq 0 ]; then
        break
    fi
    sleep 2
done

echo "Entered maintenance mode. Creating backup file."

# Backup database
DoPost https://${APPLIANCE}/admin/json-backup?license_shasum=${LICENSE_SHASUM} \
    --form "csrf_token=$CSRF_TOKEN"

echo "Exiting maintenance mode."

# Boot into default mode
DoPost https://${APPLIANCE}/admin/json-boot/default?license_shasum=${LICENSE_SHASUM} \
    --form "csrf_token=$CSRF_TOKEN"

# Wait for boot into default mode to finish
while [ 1 ]; do
    DoGet --fail https://${APPLIANCE}/admin/json-get-boot?license_shasum=${LICENSE_SHASUM}
    if [ $? -eq 0 ]; then
        break
    fi
    sleep 2
done

echo "Exited maintenance mode. Downloading backup file."

# Download the backup file
OUTPUT=aerofs-backup_$(date -u +"%Y%m%d-%H%M%S")
DoGet --output ${OUTPUT} \
    https://${APPLIANCE}/admin/download_backup_file?license_shasum=${LICENSE_SHASUM}

echo "Done. Backup file available at: ${OUTPUT}"

exit 0
