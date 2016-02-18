#!/bin/bash
# Much of this image is adapted from the following:
# https://github.com/docker-library/mysql/blob/master/5.6/docker-entrypoint.sh
# https://raw.githubusercontent.com/timhaak/docker-mariadb-alpine/master/files/start.sh
set -eo pipefail

# if command starts with an option, prepend mysqld
if [ "${1:0:1}" = '-' ]; then
	set -- mysqld_safe "$@"
fi

if [ "$1" = 'mysqld_safe' ]; then
	DATADIR="/var/lib/mysql/"
	if [ ! -d "$DATADIR/mysql" ]; then
		mkdir -p "$DATADIR"
		chown -R mysql: "$DATADIR"

		echo 'Initializing database'
		mysql_install_db --user=mysql --datadir="$DATADIR" --basedir="/usr"
		echo 'Database initialized'

		"$@" --skip-networking &
		pid="$!"

		mysql=( mysql --protocol=socket -uroot )

		for i in {30..0}; do
			if echo 'SELECT 1' | "${mysql[@]}" &> /dev/null; then
				break
			fi
			echo 'MySQL init process in progress...'
			sleep 1
		done
		if [ "$i" = 0 ]; then
			echo >&2 'MySQL init process failed.'
			exit 1
		fi

		"${mysql[@]}" <<-EOSQL
			DELETE FROM mysql.user ;
			CREATE USER 'root'@'%' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}' ;
			GRANT ALL ON *.* TO 'root'@'%' WITH GRANT OPTION ;
			FLUSH PRIVILEGES ;
		EOSQL

		mysqladmin -uroot shutdown && \
			echo 'MySQL init process done.' || \
			{
				echo >&2 'MySQL init process failed.';
				exit 1
			}
	fi

	mysql_upgrade
	chown -R mysql: "$DATADIR"
fi

exec "$@"
