#!/bin/bash
# Much of this image is adapted from the following:
# https://github.com/docker-library/mysql/blob/master/5.6/docker-entrypoint.sh
# https://raw.githubusercontent.com/timhaak/docker-mariadb-alpine/master/files/start.sh
set -eo pipefail

# if command starts with an option, prepend mysqld
if [ "${1:0:1}" = '-' ]; then
	set -- mysqld_safe "$@"
fi

fail() {
	echo >&2 "$1"
    # reset perms on failure to start to workaround weirdness seen in ENG-8010
    chown -R mysql: "$DATADIR"
    exit ${2:-1}
}

if [ "$1" = 'mysqld_safe' ]; then
	# Adjust mysql settings on beefy machines.
	# N.B. This value is the host's max memory. If the container has been run
	# with a memory modification flag (`docker run -m 1024m ...`), this is
	# inaccurate
	FREE_MEM=$(free -b | grep Mem: | cut -d ' ' -f 5)
	# N.B. Though this value is accurate when the container is run with a
	# memory limit, this value is... slightly too high... in the standard case.
	# $ docker run --rm alpine:3.3 grep hierarchical_memory_limit /sys/fs/cgroup/memory/memory.stat
	# hierarchical_memory_limit 9223372036854771712
	# Yes, that's supposed to be in bytes.
	CGROUP_MEM=$(grep hierarchical_memory_limit /sys/fs/cgroup/memory/memory.stat | cut -d ' ' -f 2)

	ACTUAL_MEM_LIMIT=$(( FREE_MEM < CGROUP_MEM ? FREE_MEM : CGROUP_MEM ))
	[ $ACTUAL_MEM_LIMIT -gt $(( 3 * 1024 * 1024 * 1024 )) ] && sed -i "s/^innodb_buffer_pool_size.*$/innodb_buffer_pool_size = 1024M/g" /etc/mysql/my.cnf

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
			fail 'MySQL init process failed.'
		fi

		"${mysql[@]}" <<-EOSQL
			DELETE FROM mysql.user ;
			CREATE USER 'root'@'%' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}' ;
			GRANT ALL ON *.* TO 'root'@'%' WITH GRANT OPTION ;
			FLUSH PRIVILEGES ;
		EOSQL

		mysqladmin -uroot shutdown && \
			echo 'MySQL init process done.' || \
			fail 'MySQL init process failed.'
	else
		chown -R mysql: "$DATADIR"
		"$@" --skip-networking &

		mysql=( mysql --protocol=socket -uroot )

		for i in {30..0}; do
			if echo 'SELECT 1' | "${mysql[@]}" &> /dev/null; then
				break
			fi
			echo 'MySQL server start in progress...'
			sleep 1
		done
		if [ "$i" = 0 ]; then
			fail 'MySQL start failed.'
		fi

		mysql_upgrade

		mysqladmin -uroot shutdown && \
			echo 'MySQL upgrade done.' || \
			fail 'MySQL upgrade failed.'
	fi

	chown -R mysql: "$DATADIR"
fi

exec "$@"
