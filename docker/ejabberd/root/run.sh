#!/bin/bash
set -e

init_db() {
    local DB_NAME=ejabberd
    /container-scripts/create-database ${DB_NAME}

    echo "Create database user and schema if not exist..."
    TABLES="$(mysql -h mysql.service ${DB_NAME} <<< 'show tables')"
    if [ "${TABLES}" = '' ]; then
        # ejabberd doesn't like empty password so we can't use the default root user.
        mysql -h mysql.service <<< "grant all on ${DB_NAME}.* to ejabberd identified by 'password'"
        mysql -h mysql.service ${DB_NAME} < /etc/ejabberd/mysql.sql
    fi
}

certify() {
    echo "Certify the service..."
    local TMP="$(mktemp -d)"
    (
        cd "${TMP}"
        /container-scripts/crt-create "$1" ca.service
    )

    cat "${TMP}/$1_ssl/$1.crt" "${TMP}/$1_ssl/$1.key" > /etc/ejabberd/ejabberd.pem
    rm -rf "${TMP}"
}

# Use the last two elements of the hostname as XMPP domain.
# This duplicates the logic in BaseParam.java
set_domain_name() {
    echo "Setting domain name..."

    # sed is to remove port number
    local HOST="$(echo "$1" | sed -e 's/:.*//')"
    local NR_DOTS="$(echo "${HOST}" | grep -o '\.' | wc -l)"
    if [ ${NR_DOTS} = 0 ] || [ ${NR_DOTS} = 1 ]; then
        echo "Bad XMPP address" >&2
        exit 11
    fi

    # Convert the host to an array
    local ARR=($(echo "${HOST}" | tr '.' ' '))
    local LEN=${#ARR[@]}
    local DOMAIN=${ARR[$(expr ${LEN} - 2)]}.${ARR[$(expr ${LEN} - 1)]}
    echo "Domain name '${DOMAIN}'"

    sed -i 's/hosts: *\[\]/hosts: ["'${DOMAIN}'"]/' /etc/ejabberd/ejabberd.yml
}

ADDRESS=$(/container-scripts/get-config-property base.xmpp.address)
certify "${ADDRESS}"
set_domain_name "${ADDRESS}"
init_db

echo "Killing old metadata for new instances of ejabberd to work..."
rm -rf /var/lib/ejabberd/*

echo "Starting ejabberd..."
service ejabberd start

while true; do
    echo "Checking ejabberd..."
    /ejabberd_check
    echo "Done"
    sleep 180
done
