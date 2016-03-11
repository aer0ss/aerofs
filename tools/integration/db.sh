#!/bin/bash

name=${1:-aerofs_db_test}
host=${2:-192.168.99.100}
port=${3:-3306}

docker rm -f $name
docker run -d --name $name -p $port:3306 aerofs/mysql

while ! echo 'select 1' | mysql -h $host -P $port -u root &>/dev/null ; do
    echo wait for mysql to come up at $host:$port
    sleep 1
done

mysql -h $host -P $port -u root <<EOF
USE mysql;
GRANT USAGE ON *.* TO 'test'@'%';
DROP USER 'test'@'%';
FLUSH PRIVILEGES;
CREATE USER 'test'@'%' IDENTIFIED BY PASSWORD '*7F7520FA4303867EDD3C94D78C89F789BE25C4EA';
GRANT ALL PRIVILEGES ON *.* TO 'test'@'%';
FLUSH PRIVILEGES;
EOF

