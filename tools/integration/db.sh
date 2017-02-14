#!/bin/bash

name=${1:-external_mysql_1}
port=${2:-3306}

THIS_DIR=$(dirname "${BASH_SOURCE[0]:-$0}")

running=$(docker ps -q -f "name=$name")

if [[ -n "$running" ]] && \
    "$THIS_DIR/../cache/img_fresh.sh" aerofs/mysql "$THIS_DIR/../../docker/mysql"; then
    echo db container already running 1>&2
elif [[ -n "$running" ]] ; then
    echo outdated db running, remove manually to force refresh 1>&2
else
    # remove any previous instance
    docker rm -fv $name >/dev/null
    docker rmi aerofs/mysql >/dev/null

    # start mysql container
    make -C $THIS_DIR/../../docker/base/base 1>&2 || exit 1
    make -C $THIS_DIR/../../docker/mysql 1>&2 || exit 1
    docker run -d --name $name -p $port:3306 --memory=512M aerofs/mysql >/dev/null || exit 1
fi

# on first startup the container may start/stop mysqld a few times
# as it performs initialization. This makes it possible for races
# to cause the creation of the test account to fail. To work around
# this we keep retrying the create step until we can successfully
# connect with the test account.
echo sanity check 1>&2
while ! docker exec -i $name mysql -u test --password=temp123 -N -s -e "select 1" &>/dev/null ; do
    while ! docker exec -i $name mysql -e "select 1" &>/dev/null ; do
        sleep 1
    done

    echo create test account 1>&2
    docker exec -i $name mysql mysql 1>&2 <<EOF
GRANT USAGE ON *.* TO 'test'@'%';
DROP USER 'test'@'%';
FLUSH PRIVILEGES;
CREATE USER 'test'@'%' IDENTIFIED BY PASSWORD '*7F7520FA4303867EDD3C94D78C89F789BE25C4EA';
GRANT ALL PRIVILEGES ON *.* TO 'test'@'%';
FLUSH PRIVILEGES;
EOF

done

VM=$(docker-machine active 2>/dev/null || echo "docker-dev")
if docker-machine ls "$VM" &>/dev/null ; then
    host=$(docker-machine ip "$VM")
else
    host=$(docker inspect $name | jq -r '.[0].NetworkSettings.IPAddress')
fi

echo export JUNIT_mysqlHost="$host:$port"

