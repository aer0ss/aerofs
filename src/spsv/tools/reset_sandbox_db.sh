#!/bin/bash -e

# This is a developer's tool. The purpose of this script is to "reset" your
# staging sp database, where reset means to delete all contents of the database
# and recreate the tables from scratch. Be careful! Do not do use this script
# with any database other than your (disposable) sandbox db!
#
# Before you can set up this script you will have to create your ~/.my.cnf
# configuration file on the sp. It should look like this:
#
# [mysql]
# user=staging_foo
# pasword=[PASSWORD]
# [mysqldump]
# user=staging_foo
# pasword=[PASSWORD]
#
# where 'foo' is replaced with your first name, and [PASSWORD] is, of course,
# your mysql password.
#
# Usage: $0 [--force]
# When --force is supplied, no user verification is done, else we verify the
# reset action with the user before it is done.

echo "WARNING: all sandbox staging db contents will be deleted!"

if [ "$1" != "--force" ]
then
    echo -n "Continue? (y/n) "
    read response

    if [ "$response" != "y" ]
    then
        echo "Aborted."
        exit 0
    fi
fi

# Need the user's first name for the database name. Pull it from git. This of
# course assumes the standard aerofs_sp_staging_<firstname> naming convention
# of sandbox staging databases.
name=$(git config user.name | awk -F' ' '{print $1}' | tr '[A-Z]' '[a-z]')

echo "Performing magic..."
kscp sp.sql staging.aerofs.com:~/

cmd="
echo drop database aerofs_sp_staging_$name | mysql;
echo create database aerofs_sp_staging_$name | mysql;
mysql aerofs_sp_staging_$name < sp.sql"

kssh -t -t staging.aerofs.com "$cmd"

echo "Database has been successfully reset. Wow, that was easy!"
