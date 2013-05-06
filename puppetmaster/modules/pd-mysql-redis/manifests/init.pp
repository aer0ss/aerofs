class pd-mysql-redis {

    include private-common

    # MySQL client and MySQL server.
    include mysql
    include mysql::server

    include redis::aof
}

