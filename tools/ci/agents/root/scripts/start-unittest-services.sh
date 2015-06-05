#!/bin/bash
set -e

echo "Starting redis ..."
service redis-server start

echo "Starting mysql ..."
set +e
service mysql start
EXIT=$?
set -e
# Exit code can be 1 for success starts :S
[[ ${EXIT} = 0 ]] || [[ ${EXIT} = 1 ]] || {
    echo ERROR: "Couldn't start MySQL"
    exit 11
}

echo "Adding mysql account test/temp123 ..."
MYSQL_PWD=temp123 mysql < $(dirname $0)/add-test-user.sql
