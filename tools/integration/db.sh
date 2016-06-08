#!/bin/bash

name=${1:-aerofs_db_test}
port=${2:-3306}

THIS_DIR=$(dirname "${BASH_SOURCE[0]:-$0}")

if [[ -n "$(docker ps -q -f "name=$name")" ]] && \
    "$THIS_DIR/../cache/img_fresh.sh" aerofs/mysql "$THIS_DIR/../../docker/mysql"; then
    echo db container already running 1>&2
else
    # remove any previous instance
    docker rm -f $name >/dev/null

    # start mysql container
    make -C $THIS_DIR/../../docker/mysql > /dev/null
    docker run -d --name $name -p $port:3306 aerofs/mysql >/dev/null
fi

# wait for mysqld to come up
while ! echo 'select 1' | docker exec -i $name mysql &>/dev/null ; do
    sleep 1
done

# create test account
docker exec -i $name mysql <<EOF
USE mysql;
GRANT USAGE ON *.* TO 'test'@'%';
DROP USER 'test'@'%';
FLUSH PRIVILEGES;
CREATE USER 'test'@'%' IDENTIFIED BY PASSWORD '*7F7520FA4303867EDD3C94D78C89F789BE25C4EA';
GRANT ALL PRIVILEGES ON *.* TO 'test'@'%';
FLUSH PRIVILEGES;
EOF

VM=${1:-$(docker-machine active 2>/dev/null || echo "docker-dev")}
if docker-machine ls "$VM" &>/dev/null ; then
    host=$(docker-machine ip "$VM")
else
    host=$(docker inspect aerofs_db_test | jq -r '.[0].NetworkSettings.IPAddress')
fi

echo export JUNIT_mysqlHost="$host:$port"

